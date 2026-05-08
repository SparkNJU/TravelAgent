# Step 15 交付物：导出任务自动清理与删除接口（2026-04-27）

## 1. 本步目标

1. 为导出任务增加自动清理能力，避免历史任务无限增长。
2. 支持单个导出任务删除与批量删除。
3. 删除任务时同步尝试删除导出文件。

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
2. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`
3. `backend/src/main/java/com/tripagent/backend/BackendApplication.java`
4. `backend/src/main/resources/application.yml`
5. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportTaskBatchDeleteRequest.java`
6. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportTaskBatchDeleteResponse.java`

新增能力：

1. 定时清理：
- `@Scheduled` 周期执行
- 按 `retention-days` 清理终态任务（`SUCCEEDED/FAILED`）

2. 删除接口：
- `DELETE /api/eval/exports/{exportId}`
- `POST /api/eval/exports/batch-delete`

3. 配置项：
- `eval.export.cleanup.enabled`
- `eval.export.cleanup.retention-days`
- `eval.export.cleanup.interval-ms`

## 3. 验证建议

1. 创建若干导出任务并确认列表可查。
2. 调用单个删除接口，确认任务消失。
3. 调用批量删除接口，确认返回成功/失败统计。
4. 调整清理间隔和保留天数进行本地快速验证。
