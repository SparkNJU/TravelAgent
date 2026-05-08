# Step 17 交付物：导出任务持久化入库（2026-04-27）

## 1. 本步目标

1. 将导出任务元数据从内存 Map 切换为数据库持久化。
2. 保持现有导出 API 契约不变，前端无需改造即可继续使用。
3. 支持服务重启后继续查询历史导出任务记录。

## 2. 后端改造

变更文件：

1. `backend/src/main/resources/db/schema.sql`
2. `backend/src/main/java/com/tripagent/backend/entity/enums/ExportTaskStatus.java`
3. `backend/src/main/java/com/tripagent/backend/entity/EvalExportTask.java`
4. `backend/src/main/java/com/tripagent/backend/repository/EvalExportTaskRepository.java`
5. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`

新增能力：

1. 新增 `eval_export_task` 表保存导出任务元数据。
2. 新增 `EvalExportTask` 实体与 `ExportTaskStatus` 枚举。
3. 新增 `EvalExportTaskRepository`，支持任务分页筛选与过期清理查询。
4. `EvalExportService` 全量改为 JPA 持久化逻辑：
- 创建导出任务
- 查询导出任务与分页列表
- 失败重试
- 单删与批删
- 定时清理

## 3. 验证建议

1. 创建导出任务后重启后端服务，确认导出任务列表仍可查询。
2. 对失败导出任务执行重试，确认状态流转为 `PENDING -> RUNNING -> SUCCEEDED/FAILED`。
3. 执行单删与批删，确认数据库记录与导出文件同步删除。
4. 观察定时清理后，过期终态任务记录与文件被删除。
