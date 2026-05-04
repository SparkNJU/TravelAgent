# Step 14 交付物：导出失败重试与前端闭环（2026-04-27）

## 1. 本步目标

1. 导出任务失败后支持重试。
2. 前端支持对失败导出任务发起重试。
3. 重试后继续轮询状态并在成功后下载。

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
2. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`

新增接口：

1. `POST /api/eval/exports/{exportId}/retry`

行为说明：

1. 仅 `FAILED` 状态允许重试。
2. 重试会重置任务状态为 `PENDING`，并再次异步执行导出。
3. 保留原 `exportId`，便于前端追踪同一任务生命周期。

## 3. 前端改造

变更文件：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`

能力新增：

1. 新增 `retryExportTask(exportId)` API。
2. 导出任务列表中 `FAILED` 项展示“重试”按钮。
3. 点击重试后自动刷新列表并复用轮询逻辑。

## 4. 验证建议

1. 构造一次失败导出任务。
2. 点击“重试”，确认状态变为 `PENDING/RUNNING`。
3. 重试成功后确认可下载导出文件。
