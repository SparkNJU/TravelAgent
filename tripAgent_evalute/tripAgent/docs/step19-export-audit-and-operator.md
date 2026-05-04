# Step 19 交付物：导出任务操作者与审计能力（2026-04-27）

## 1. 本步目标

1. 为导出任务补齐操作者、来源、来源 IP 等审计字段。
2. 记录导出任务关键行为审计日志。
3. 提供导出任务审计记录查询接口。

## 2. 后端改造

变更文件：

1. `backend/src/main/resources/db/schema.sql`
2. `backend/src/main/java/com/tripagent/backend/entity/EvalExportTask.java`
3. `backend/src/main/java/com/tripagent/backend/entity/EvalExportAudit.java`
4. `backend/src/main/java/com/tripagent/backend/repository/EvalExportAuditRepository.java`
5. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportTaskResponse.java`
6. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportAuditResponse.java`
7. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportAuditPageResponse.java`
8. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
9. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`

新增能力：

1. 任务元数据新增字段：
- `createdBy`
- `source`
- `sourceIp`
- `lastOperator`
- `lastOperationAt`

2. 审计表 `eval_export_audit` 记录动作：
- 创建、启动、成功、失败
- 重试、删除、清理
- 一致性修复动作

3. 新增接口：
- `GET /api/eval/exports/{exportId}/audits?page=&size=`

## 3. 验证建议

1. 创建导出任务后查看返回字段中 `createdBy/source/sourceIp`。
2. 执行重试、删除后查询审计列表。
3. 确认审计记录时间、动作、操作者与来源 IP 正确。
