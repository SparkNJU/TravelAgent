# 技术说明文档 2：Evaluator 评测系统

## 1. 文档目标

本文档说明 `evaluator` 项目的当前技术实现，重点覆盖系统架构、技术栈、评测配置、任务执行链路、Agent/Ragas judge、Bradley-Terry 多模型评测、数据模型和接口设计。

当前版本已经完成 AI 评测配置去版本化：任务不再绑定策略版本，`eval_strategy_version` 与 `strategy_version` 已从数据库和代码路径中下线。评测配置以 `eval_strategy` 的最终配置形态直接复用。

## 2. 总体架构

项目位于 `evaluator/`，由三部分组成：

| 模块 | 路径 | 技术栈 | 职责 |
| --- | --- | --- | --- |
| 前端 | `evaluator/frontend` | Vue 3、TypeScript、Vite、Axios、Vue Router | 任务创建、评测配置、模型管理、数据集管理、运行详情展示 |
| 后端 | `evaluator/backend` | Spring Boot 3.2、Java 17、JPA、WebFlux、H2 | API、任务调度、评测执行、Ragas/LLM 调用、BT 排名、结果落库 |
| Agent | `evaluator/agent` | FastAPI、Uvicorn、Pydantic、Ragas、LangChain、OpenAI SDK | 流式对话接口、Ragas judge 评分接口 |

整体链路：

```text
用户页面操作
  -> frontend 组装请求
  -> backend 保存任务 / 启动 run
  -> EvalRunService 异步执行
  -> 调用模型、Agent 或 judge
  -> 写入 qa_record / metric_snapshot / eval_comparison / model_rating
  -> 前端运行详情页展示结果
```

## 3. 前端结构

前端入口在 `evaluator/frontend/src`。

| 文件 | 作用 |
| --- | --- |
| `api/client.ts` | 封装评测任务、策略、模型、数据集、运行结果等 API |
| `views/TasksView.vue` | 评测任务列表、创建任务入口、启动/删除/对比 |
| `components/CreateTaskModal.vue` | 创建任务主弹窗，支持本地评测配置一键应用 |
| `views/StrategiesView.vue` | AI 评测配置管理，配置已去版本化 |
| `components/ModelManagementPanel.vue` | 模型配置管理 |
| `components/DatasetManagementPanel.vue` | 数据集上传与管理 |
| `views/RunDetailView.vue` | 运行详情、样本结果、指标、BT 排名与确定性对比 |
| `components/ModelPickerSection.vue` | 参评模型和裁判模型选择 |

前端创建任务时主要提交：

```text
taskName
agentVersion
datasetId
evaluationMode
evaluationMethod
evaluationDimensions
metricSet
strategyConfig
selectedModelIds
judgeModelId
comparisonSamplingStrategy
positionSwapEnabled
```

## 4. 后端结构

后端入口是 `BackendApplication`，核心包位于 `com.tripagent.backend`。

| 层次 | 关键类 | 说明 |
| --- | --- | --- |
| Controller | `EvalTaskController` | 任务、运行、指标、导出、SSE 接口 |
| Controller | `EvalStrategyController` | 评测配置、自定义指标接口 |
| Controller | `DatasetController` | 数据集上传、查询、软删除 |
| Controller | `ModelProfileController` | 模型配置增删改查 |
| Controller | `EvalRatingController` | BT 评分和排序查询 |
| Service | `EvalTaskService` | 创建任务、更新任务、启动 run、BT 配置校验 |
| Service | `EvalRunService` | 单模型评测、Ragas judge、BT 多模型评测主流程 |
| Service | `RatingService` | Bradley-Terry 拟合、Elo 转换、置信区间、OVERALL 合成 |
| Service | `RagasGatewayService` | Java 后端调用 Python agent 的 Ragas 评分接口 |
| Service | `LlmGateway` | OpenAI 兼容模型调用网关 |

## 5. Agent 端工具链

Agent 服务位于 `evaluator/agent`，依赖来自 `requirements.txt`：

| 工具 | 用途 |
| --- | --- |
| `FastAPI` | 暴露 HTTP 接口 |
| `Uvicorn` | 启动 ASGI 服务 |
| `Pydantic` | 请求和响应数据校验 |
| `StreamingResponse` | `/agent/chat/stream` 返回 SSE 流式回答 |
| `Ragas` | `/eval/ragas/score` 执行 judge 指标评分 |
| `LangChain`、`langchain-openai` | 包装 OpenAI 兼容模型调用 |
| `openai` | 调用 ModelScope OpenAI-compatible endpoint |
| `datasets` | 组织 Ragas 评测输入 |
| `python-dotenv` | 环境变量加载 |

Agent 暴露两个核心接口：

| 接口 | 作用 |
| --- | --- |
| `GET /agent/chat/stream` | 按 SSE 返回 Agent 回答，后端消费后写入 `qa_record` |
| `POST /eval/ragas/score` | 接收 question/answer/groundTruth，返回 Ragas 指标分 |

当前 Ragas 支持指标：

```text
faithfulness
answer_correctness
```

如果 Ragas 不可用或返回 NaN，系统会返回 fallback 分数，避免单次 judge 故障阻断整轮评测。

## 6. 评测配置设计

评测配置表为 `eval_strategy`，字段包括：

```text
strategy_id
strategy_name
metric_definitions
weight_config
threshold_config
created_at
```

配置版本相关内容已经下线：

```sql
ALTER TABLE eval_task DROP COLUMN IF EXISTS strategy_version;
DROP TABLE IF EXISTS eval_strategy_version;
```

前端创建任务时可以选择本地评测配置并一键应用。应用后会把配置中的评测模式、评测方法、维度、指标、模型和推理参数带入任务表单，最终保存到 `eval_task.strategy_config` 等字段。

### 6.1 评测模式

| 模式 | 含义 | 使用场景 |
| --- | --- | --- |
| `RESULT` | 只评估输入与最终输出 | 最终回答质量评估 |
| `PROCESS` | 额外关注工具轨迹 `toolTrace` | Agent 过程合理性评估 |

### 6.2 评测方法

| 方法 | 含义 | 主要链路 |
| --- | --- | --- |
| `DETERMINISTIC` | 规则匹配，检查期望关键词或结构是否命中 | 本地规则计算 |
| `JUDGE` | 使用裁判模型或 Ragas 对回答做语义评分 | Java 后端 -> Python agent Ragas |
| `HYBRID` | 规则分与 judge 分组合 | `0.5 * deterministic + 0.5 * judge` |

### 6.3 评测维度

系统支持：

```text
EFFECTIVENESS
SAFETY
PERFORMANCE
OVERALL
```

兼容历史命名：`EFFICIENCY` 会被解析为 `EFFECTIVENESS`。

## 7. 普通评测执行流程

入口为 `EvalTaskService.startTask(taskId)`：

```text
1. 查询 EvalTask
2. 将任务状态置为 RUNNING
3. 创建 EvalRun
4. 异步调用 EvalRunService.executeRunAsync(runId)
```

`executeRunAsync` 会先判断是否满足 BT 多模型条件：

```text
evaluationMethod in {JUDGE, HYBRID}
selectedModelIds >= 2
judgeModelId != null
```

如果不满足，则走普通单模型或 Agent 评测：

```text
1. 读取数据集样本
2. 解析 strategyConfig 中的推理配置
3. 如果选择了单模型，走 LlmGateway.invokeProfile
4. 如果未选择单模型，走 AgentGatewayService.streamAnswer
5. 对每条样本生成 QaRecord
6. JUDGE/HYBRID 时批量调用 RagasGatewayService.score
7. 调 evaluateSample 计算 finalScore
8. 聚合写入 MetricSnapshot
9. 更新 EvalRun 和 EvalTask 状态
```

样本评分逻辑：

```text
DETERMINISTIC: finalScore = deterministicScore
JUDGE:         finalScore = judgeScore
HYBRID:        finalScore = 0.5 * deterministicScore + 0.5 * judgeScore
```

修正规则：

```text
PROCESS 模式缺少有效 toolTrace 时降低分数
回答包含不安全词时降低分数
finalScore >= 0.6 判定样本通过
```

运行级指标写入 `metric_snapshot`：

```text
taskCompletionRate
toolCorrectnessScore
toolEfficiencyScore
firstTokenP95
endToEndP95
totalTokens
effectivenessScore
safetyScore
performanceScore
judgeReason
```

## 8. BT 多模型评测流程

BT 流程用于比较多个模型的相对表现。它不是给单个回答打绝对分，而是通过裁判模型进行成对比较，再用 Bradley-Terry 模型拟合排名。

触发条件：

```text
evaluationMethod = JUDGE 或 HYBRID
selectedModelIds 至少 2 个
judgeModelId 存在
```

执行链路：

```text
1. 每条样本调用所有 player 模型生成回答
2. 每个 player 的回答写入一条 qa_record
3. 按 ALL_PAIRS 生成模型对
4. 对每个模型对、每个维度做比较
5. EFFECTIVENESS / SAFETY / OVERALL 调用裁判模型
6. PERFORMANCE 使用本地延迟比较
7. 比较结果写入 eval_comparison
8. RatingService.computeAndPersist 拟合 BT 排名
9. 排名结果写入 model_rating
```

Bradley-Terry 原理：

```text
每个模型有一个潜在能力值 theta。
模型 i 战胜模型 j 的概率由 theta_i - theta_j 决定。
系统收集 A 胜 B、B 胜 C、平局等比较结果。
BradleyTerryFitter 通过最大似然估计拟合每个模型的 theta。
EloConverter 将 theta 转为更易展示的 Elo 分。
BootstrapCi 给出 95% 置信区间。
```

BT 结果字段：

```text
theta
elo
lower_ci_95
upper_ci_95
n_comparisons
n_wins
win_rate
avg_latency_ms
avg_tokens
completion_rate
```

`OVERALL` 维度由各维度 Elo 按权重合成，默认权重：

```text
EFFECTIVENESS = 0.5
SAFETY        = 0.2
PERFORMANCE   = 0.3
```

## 9. 数据模型

核心表：

| 表 | 说明 |
| --- | --- |
| `eval_task` | 评测任务配置和状态 |
| `eval_run` | 任务运行实例 |
| `qa_record` | 样本级输入、期望输出、实际输出、工具轨迹和错误信息 |
| `metric_snapshot` | run 级聚合指标 |
| `eval_strategy` | 去版本化后的本地评测配置 |
| `custom_metric` | 自定义指标 |
| `dataset` | 数据集元信息 |
| `dataset_sample` | 数据集样本 |
| `model_profile` | 模型配置 |
| `eval_comparison` | BT 成对比较结果 |
| `model_rating` | BT 拟合后的模型评分 |
| `eval_export_task` | 对比导出任务 |
| `eval_export_audit` | 导出审计记录 |

## 10. 接口清单

### 10.1 任务与运行

```text
POST   /api/eval/tasks
GET    /api/eval/tasks
GET    /api/eval/tasks/{taskId}
PUT    /api/eval/tasks/{taskId}
DELETE /api/eval/tasks/{taskId}
POST   /api/eval/tasks/{taskId}/start
POST   /api/eval/tasks/{taskId}/cancel
GET    /api/eval/tasks/{taskId}/runs
GET    /api/eval/runs/{runId}
GET    /api/eval/runs/{runId}/records
GET    /api/eval/runs/{runId}/metrics
GET    /api/eval/runs/{runId}/stream
```

### 10.2 评测配置与指标

```text
POST /api/eval/strategies
GET  /api/eval/strategies
GET  /api/eval/strategies/{strategyId}
PUT  /api/eval/strategies/{strategyId}
POST /api/eval/metrics/custom
GET  /api/eval/metrics/custom
```

### 10.3 数据集与模型

```text
GET    /api/eval/datasets
GET    /api/eval/datasets/{id}
GET    /api/eval/datasets/{id}/samples
POST   /api/eval/datasets
DELETE /api/eval/datasets/{id}

POST   /api/eval/models
GET    /api/eval/models
GET    /api/eval/models/{id}
PUT    /api/eval/models/{id}
DELETE /api/eval/models/{id}
GET    /api/eval/models/catalog
GET    /api/eval/llm/ping
```

### 10.4 BT 排名与导出

```text
GET /api/eval/runs/{runId}/ratings
GET /api/eval/runs/{runId}/ranked

GET  /api/eval/tasks/{taskId}/runs/compare
POST /api/eval/tasks/{taskId}/runs/compare/export
GET  /api/eval/exports
GET  /api/eval/exports/{exportId}
GET  /api/eval/exports/{exportId}/download
POST /api/eval/exports/{exportId}/retry
DELETE /api/eval/exports/{exportId}
POST /api/eval/exports/batch-delete
GET  /api/eval/exports/{exportId}/audits
GET  /api/eval/exports/consistency-check
GET  /api/eval/exports/metrics
```

## 11. 配置与部署

后端配置文件：`evaluator/backend/src/main/resources/application.yml`

关键配置：

```yaml
server:
  port: 8080

agent:
  base-url: http://localhost:8000

llm:
  openai-compatible:
    base-url: https://api-inference.modelscope.cn/v1
    api-key: ${MODELSCOPE_API_KEY:}
    judge-api-key: ${JUDGE_API_KEY:}
    timeout-seconds: 60
    max-retries: 2

bt:
  bootstrap-rounds: 200
  elo-anchor: 1000
  elo-scale: 400
  fit-max-iter: 200
  fit-lr: 0.1
```

Docker Compose：

```text
backend: 8080
agent:   8000
frontend:5173
```

启动方式：

```bash
cd evaluator
docker compose up --build
```

本地开发：

```bash
# backend
cd evaluator/backend
mvn spring-boot:run

# agent
cd evaluator/agent
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000

# frontend
cd evaluator/frontend
npm install
npm run dev
```

## 12. 可靠性设计

1. 任务运行采用异步执行，前端通过 run 详情和 SSE 查看进度。
2. Ragas 失败时返回 fallback 分数，避免 judge 服务异常导致整轮任务失败。
3. BT 排名计算前会先落 `eval_comparison`，拟合失败不会影响已有样本记录。
4. 导出任务有审计表和一致性检查接口。
5. 启动恢复服务会处理异常中断后的运行状态。
6. 模型调用支持 timeout 和 maxRetries。

## 13. 当前边界

1. 默认数据库为本地 H2，适合课程演示和轻量部署；生产化需要迁移到 MySQL/PostgreSQL。
2. Ragas 目前只开放 `faithfulness` 和 `answer_correctness`。
3. BT 当前采样策略以 `ALL_PAIRS` 为主，模型数量较多时比较次数增长较快。
4. `PROCESS` 模式依赖有效 `toolTrace`，直接模型调用时过程信息较少。
5. 评测配置已去版本化，历史回滚不再通过策略版本实现，需要依赖外部备份或任务快照。

## 14. 总结

Evaluator 当前形成了完整闭环：

```text
模型管理 -> 数据集管理 -> 本地评测配置 -> 创建任务 -> 异步运行 -> 样本记录 -> 指标聚合 -> 结果展示 / BT 排名 / 导出
```

其中普通评测覆盖 `DETERMINISTIC`、`JUDGE`、`HYBRID` 三种方法；多模型评测通过裁判模型生成成对比较，并使用 Bradley-Terry 拟合模型能力和 Elo 排名。评测配置去版本化后，用户在创建任务时可以直接复用最终配置，减少重复填写并提升任务创建效率。
