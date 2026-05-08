# Step 12 交付物：异步导出任务与下载（2026-04-25）

## 1. 本步目标

1. 将运行对比导出从“前端本地拼文件”升级为“后端异步导出任务”。
2. 支持导出任务创建、状态查询、完成后下载。
3. 前端保留对比能力，同时提供导出状态可视化。

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`
2. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
3. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportTaskResponse.java`

说明：

1. 当前导出任务采用 `EvalExportService` 内存任务表实现（`ConcurrentHashMap`）。
2. 导出文件写入 `backend/target/exports`，并通过下载接口提供文件流。

接口新增：

1. `POST /api/eval/tasks/{taskId}/runs/compare/export`
2. `GET /api/eval/exports/{exportId}`
3. `GET /api/eval/exports/{exportId}/download`

后端行为：

1. 创建导出任务后立即返回 `exportId`。
2. 后台线程执行导出并更新状态：`PENDING -> RUNNING -> SUCCEEDED/FAILED`。
3. 导出成功后返回 `downloadUrl`，前端可直接下载文件。

## 3. 前端改造

变更文件：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`

能力新增：

1. API 增加：
- `createRunCompareExportTask(...)`
- `getExportTask(exportId)`

2. 对比面板新增：
- 导出格式选择（json/csv）
- 异步导出按钮
- 导出任务状态展示

3. 轮询策略：
- 每 1 秒查询一次导出状态。
- 最多轮询 30 次。
- 成功后自动打开下载链接。

## 4. 验证建议

1. 在对比结果已生成后点击“异步导出”。
2. 观察导出状态从 `PENDING/RUNNING` 变化到 `SUCCEEDED`。
3. 校验浏览器是否自动打开下载地址。
4. 下载文件后检查内容是否与对比参数一致。
