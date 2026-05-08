# Step 4 交付物：执行编排与运行查询（2026-04-24）

## 1. 目标

完成运行层能力：任务启动后可异步执行样本、落库 QARecord、聚合 MetricSnapshot，并开放 records/metrics/stream 查询接口。

## 2. 已完成接口

基路径：`/api/eval`

1. `GET /runs/{runId}/records`
- 返回样本级记录（输入、期望、实际、toolTrace、时延、token、错误信息）

2. `GET /runs/{runId}/metrics`
- 返回运行级聚合指标（完成率、工具正确性、工具效率、P95、总 token 等）

3. `GET /runs/{runId}/stream`
- 返回运行过程 SSE 事件流（run_started、sample_start、sample_done、run_done、error）

## 3. 执行编排实现

1. `POST /tasks/{taskId}/start` 现在会：
- 创建 RUNNING 状态 run
- 触发异步执行 `executeRunAsync(runId)`

2. 异步执行阶段：
- 构造样本集（当前为 MVP 内置样本）
- 逐条写入 `qa_record`
- 实时更新 run 的 success/fail/total
- 计算并写入 `metric_snapshot`
- 更新 run/task 最终状态

## 4. 代码落点

1. 运行服务：
- `backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java`

2. 控制器扩展：
- `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`

3. DTO：
- `backend/src/main/java/com/tripagent/backend/dto/eval/QaRecordResponse.java`
- `backend/src/main/java/com/tripagent/backend/dto/eval/MetricSnapshotResponse.java`

4. Repository 扩展：
- `backend/src/main/java/com/tripagent/backend/repository/QaRecordRepository.java`

5. 异步支持：
- `backend/src/main/java/com/tripagent/backend/BackendApplication.java`（`@EnableAsync`）

## 5. 当前边界（MVP）

1. 样本来源目前使用内置样本，下一步会接入真实数据集解析。
2. 指标计算目前为显式规则聚合，下一步接入 LLM-as-a-Judge。
3. SSE 采用内存 emitter 管理，下一步可接消息中间件。

## 6. 验收清单

1. 启动任务后 run 状态从 RUNNING 转 SUCCEEDED/FAILED。
2. `records` 接口可返回至少 3 条样本记录。
3. `metrics` 接口可返回 Step1 定义的核心显式指标。
4. `stream` 接口可持续收到运行事件并正常结束。
