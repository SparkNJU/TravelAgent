# Step 5 交付物：策略版本与自定义指标（2026-04-24）

## 1. 目标

完成策略管理、自定义指标注册 API，并将策略权重与自定义指标接入运行评分流程。

## 2. 已完成接口

基路径：`/api/eval`

1. `POST /strategies`
- 创建评测策略

2. `GET /strategies`
- 查询策略列表（包含 latestVersion）

3. `GET /strategies/{strategyId}`
- 查询策略详情

4. `POST /strategies/{strategyId}/versions`
- 创建策略版本（支持自动版本号）

5. `POST /metrics/custom`
- 注册自定义指标

6. `GET /metrics/custom?enabledOnly=true|false`
- 查询自定义指标（联调辅助接口）

## 3. 评分流程接入

在运行执行服务中新增：

1. 策略权重应用
- 读取任务绑定的 `strategyVersion`（按策略版本 ID）
- 解析 `weightConfig` 与 `thresholdConfig`
- 计算 overallScore
- 按 overallThreshold 与 safetyMin 做门禁判断

2. 自定义指标执行
- 根据 `metricSet` 过滤可用指标（支持 JSON 数组或逗号分隔 ID）
- 计算每个指标 score/pass
- 将自定义指标结果融入 effectivenessScore

3. 结果落库
- `MetricSnapshot.judgeReason` 写入结构化 JSON（策略+门限+自定义指标）
- SSE 追加 `strategy_applied` 事件

## 4. 代码落点

1. 控制器
- `backend/src/main/java/com/tripagent/backend/controller/eval/EvalStrategyController.java`

2. 服务
- `backend/src/main/java/com/tripagent/backend/service/eval/EvalStrategyService.java`
- `backend/src/main/java/com/tripagent/backend/service/eval/CustomMetricService.java`
- `backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java`（评分逻辑增强）

3. DTO
- `CreateEvalStrategyRequest`
- `EvalStrategyResponse`
- `CreateStrategyVersionRequest`
- `EvalStrategyVersionResponse`
- `CreateCustomMetricRequest`
- `CustomMetricResponse`

## 5. 验收清单

1. 可创建策略并查询策略列表与详情。
2. 可创建策略版本并自动递增版本号。
3. 可注册至少 1 个自定义指标。
4. 启动任务后 `metrics` 接口返回的 `judgeReason` 包含策略和自定义指标结果。
5. `stream` 接口可收到 `strategy_applied` 事件。
