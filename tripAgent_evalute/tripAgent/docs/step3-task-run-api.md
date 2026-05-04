# Step 3 交付物：任务与运行 API（2026-04-24）

## 1. 目标

完成第三步：实现评测任务管理与运行管理的核心 API，支撑前端创建任务、启动任务、查询运行状态。

## 2. 已完成接口

基路径：`/api/eval`

1. `POST /tasks`
- 创建评测任务

2. `GET /tasks`
- 查询任务列表（支持 `status`、`agentVersion` 可选过滤）

3. `GET /tasks/{taskId}`
- 查询任务详情

4. `PUT /tasks/{taskId}`
- 更新任务配置（RUNNING 状态会阻止关键参数修改）

5. `POST /tasks/{taskId}/start`
- 启动任务，生成并返回 `runId`

6. `GET /runs/{runId}`
- 查询运行状态与统计字段

## 3. 已完成代码

### 3.1 Controller
- `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`
- `backend/src/main/java/com/tripagent/backend/controller/eval/EvalExceptionHandler.java`

### 3.2 Service
- `backend/src/main/java/com/tripagent/backend/service/eval/EvalTaskService.java`

### 3.3 DTO
- `backend/src/main/java/com/tripagent/backend/dto/eval/EvalApiResponse.java`
- `backend/src/main/java/com/tripagent/backend/dto/eval/CreateEvalTaskRequest.java`
- `backend/src/main/java/com/tripagent/backend/dto/eval/UpdateEvalTaskRequest.java`
- `backend/src/main/java/com/tripagent/backend/dto/eval/EvalTaskResponse.java`
- `backend/src/main/java/com/tripagent/backend/dto/eval/EvalRunResponse.java`

## 4. 交互约定

1. 成功响应：`{ code: 200, message: "ok", data: ... }`
2. 参数错误：HTTP 400
3. 状态冲突（如 RUNNING 更新任务）：HTTP 409
4. 服务异常：HTTP 500

## 5. 当前实现边界

1. `start` 仅完成 run 记录创建与状态流转，不含样本执行编排。
2. records/metrics/stream 查询将在下一步实现。

## 6. Step 3 验收清单

1. 可创建任务并返回 `taskId`。
2. 可按状态和版本查询任务列表。
3. 可更新非运行中任务。
4. 可启动任务并返回 `runId`。
5. 可通过 `runId` 查询运行状态。
6. 异常场景返回统一错误结构。
