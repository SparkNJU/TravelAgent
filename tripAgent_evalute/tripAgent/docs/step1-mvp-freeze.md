# Step 1 交付物：MVP 范围冻结（2026-04-24）

## 1. 文档目标

在当天开发开始前，冻结最小可用范围（MVP），避免需求蔓延，确保今天可以完成可演示闭环。

闭环定义：创建任务 -> 启动执行 -> 样本落库 -> 指标聚合 -> 页面展示 -> 定位问题。

## 2. 今日范围冻结

### 2.1 P0（今天必须完成）

1. 评测任务管理
- 创建任务
- 查询任务列表
- 查询任务详情
- 更新任务配置

2. 执行编排与运行管理
- 启动任务执行
- 生成 runId
- run 状态流转（READY/RUNNING/SUCCEEDED/FAILED）

3. 样本级记录
- 样本输入/期望输出/实际输出
- 工具轨迹（toolTrace）
- 时延、Token、错误信息

4. 指标聚合（显式指标）
- taskCompletionRate
- toolCorrectnessScore
- toolEfficiencyScore
- firstTokenP95
- endToEndP95
- totalTokens

5. 策略与版本
- 创建策略
- 查询策略
- 创建策略版本
- 任务绑定策略版本并生效

6. 自定义指标（最小可用）
- 注册至少 1 个自定义指标
- 任务执行中可引用并输出该指标结果

7. 前端联调
- 任务列表页接真实接口
- 创建任务与启动执行可用
- 运行详情页显示 records + metrics
- 样本监控页显示 timeline + toolTrace

### 2.2 P1（有余力再做）

1. 结果导出（CSV/JSON）
2. 结果对比页（多 run 并排）
3. 更细粒度筛选器（多条件组合）
4. 指标趋势图（按时间）

### 2.3 今日不做（明确排除）

1. 复杂权限体系（RBAC/多角色审批）
2. 多租户隔离
3. 完整 LLM-as-a-Judge 可配置链路
4. 报表视觉深度打磨
5. 高级回滚编排（跨版本自动迁移）

## 3. 接口冻结（MVP 版本）

以下接口作为今天开发基线，不在当天临时扩展字段语义。

### 3.1 任务相关

1. POST /api/eval/tasks
- 用途：创建任务
- 最小请求字段：taskName, agentVersion, datasetId, evaluationMode, evaluationMethod, evaluationDimensions, strategyVersion
- 返回：taskId, status, createdAt

2. GET /api/eval/tasks
- 用途：任务列表
- 最小查询字段：status, agentVersion（可选）
- 返回：任务摘要列表

3. GET /api/eval/tasks/{taskId}
- 用途：任务详情
- 返回：完整任务配置

4. PUT /api/eval/tasks/{taskId}
- 用途：更新任务配置
- 约束：RUNNING 状态不可改关键执行参数

5. POST /api/eval/tasks/{taskId}/start
- 用途：启动执行
- 返回：runId, status=RUNNING

### 3.2 运行结果相关

1. GET /api/eval/runs/{runId}
- 用途：查询运行状态

2. GET /api/eval/runs/{runId}/records
- 用途：查询样本级结果

3. GET /api/eval/runs/{runId}/metrics
- 用途：查询聚合指标

4. GET /api/eval/runs/{runId}/stream
- 用途：SSE 运行进度与事件

### 3.3 策略与指标相关

1. POST /api/eval/strategies
- 用途：创建策略

2. GET /api/eval/strategies
- 用途：查询策略列表

3. GET /api/eval/strategies/{strategyId}
- 用途：策略详情

4. POST /api/eval/strategies/{strategyId}/versions
- 用途：创建策略版本

5. POST /api/eval/metrics/custom
- 用途：注册自定义指标

## 4. 数据字段冻结（MVP 版本）

### 4.1 EvalTask

- taskId
- taskName
- agentVersion
- datasetId
- metricSet
- status
- createdAt
- evaluationMode
- evaluationMethod
- evaluationDimensions
- strategyConfig
- strategyVersion

### 4.2 EvalRun

- runId
- taskId
- status
- startTime
- endTime
- totalCount
- successCount
- failCount

### 4.3 QARecord

- qaId
- runId
- input
- expectedOutput
- actualOutput
- toolTrace
- firstTokenLatencyMs
- endToEndLatencyMs
- tokenUsage
- errorCode
- errorMessage

### 4.4 MetricSnapshot

- runId
- taskCompletionRate
- toolCorrectnessScore
- toolEfficiencyScore
- firstTokenP95
- endToEndP95
- totalTokens
- effectivenessScore
- safetyScore
- performanceScore
- judgeReason

### 4.5 EvalStrategy

- strategyId
- strategyName
- metricDefinitions
- weightConfig
- thresholdConfig
- version
- createdAt

## 5. 事件协议冻结（Agent -> Backend）

标准事件类型：

1. tool_call
2. tool_result
3. answer_chunk
4. done
5. error

最小公共字段：

- runId
- sampleId
- timestamp
- eventType
- payload

## 6. 验收清单（今天必须打勾）

1. 可以创建任务并在列表查询到。
2. 可以启动任务并拿到 runId。
3. 一个 run 至少产出 3 条 QARecord。
4. metrics 接口返回至少 6 个显式指标。
5. 运行详情页展示 records + metrics。
6. 样本监控页展示 toolTrace 与错误阶段。
7. 至少 1 个策略版本可被任务引用并影响结果。
8. 至少 1 个自定义指标可注册、可执行、可展示。
9. 完成一次端到端演示录屏或演示脚本。

## 7. 今日排期建议（可直接执行）

1. 09:30-10:30：建表与实体
2. 10:30-12:00：任务 API + 启动 API
3. 13:30-15:00：执行编排 + 样本落库
4. 15:00-16:00：显式指标聚合
5. 16:00-17:00：策略版本 + 自定义指标
6. 17:00-18:30：前端联调与验收

## 8. 风险与降级策略

1. 风险：Agent 事件结构不稳定
- 降级：后端做事件容错映射，未知字段落 rawEvent。

2. 风险：指标计算耗时过长
- 降级：先异步聚合，页面轮询状态。

3. 风险：前后端字段不一致
- 降级：以本文件为单一事实来源（SSOT），临时字段必须登记。

## 9. 版本与变更规则

1. 本文件版本：v1.0（2026-04-24）
2. 今日开发期间，新增字段需先更新本文件后再改代码。
3. 超出 P0 的功能默认进入下一迭代，不插入今天主线。
