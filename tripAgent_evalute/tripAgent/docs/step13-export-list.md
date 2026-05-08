# Step 13 交付物：导出任务列表与状态筛选（2026-04-27）

## 1. 本步目标

1. 补齐导出任务历史查询能力，支持分页。
2. 支持按任务与状态筛选导出任务。
3. 前端可查看导出任务列表并快速下载成功结果。

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportTaskPageResponse.java`
2. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
3. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`

新增接口：

1. `GET /api/eval/exports?taskId=&status=&page=&size=`

返回结构：

1. `items`
2. `page`
3. `size`
4. `total`
5. `totalPages`
6. `hasNext`

## 3. 前端改造

变更文件：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`
3. `frontend/src/style.css`

能力新增：

1. 新增 `listExportTasks(...)` API。
2. 在运行对比区域新增“导出任务列表”面板。
3. 支持状态筛选与分页切换。
4. 成功任务可一键下载。

## 4. 验证建议

1. 先完成一次异步导出。
2. 刷新导出列表，确认任务可见。
3. 切换状态筛选与翻页，确认返回正确。
4. 对 `SUCCEEDED` 任务点击下载，确认文件可获取。
