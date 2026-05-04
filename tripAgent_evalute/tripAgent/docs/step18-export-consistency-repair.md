# Step 18 交付物：导出任务与文件一致性巡检修复（2026-04-27）

## 1. 本步目标

1. 支持扫描导出任务记录与磁盘导出文件的一致性。
2. 识别两类问题：
- 任务指向文件缺失（missing file）
- 磁盘孤儿文件（orphan file）
3. 支持一键修复：
- 缺失文件任务自动标记失败
- 孤儿文件自动删除

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
2. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`
3. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportConsistencyResponse.java`

新增接口：

1. `GET /api/eval/exports/consistency-check?repair=false`

行为说明：

1. `repair=false`：仅巡检，不改数据。
2. `repair=true`：执行修复并返回修复统计。
3. 巡检目录固定为 `backend/target/exports`。

## 3. 验证建议

1. 手动删除一个 `SUCCEEDED` 任务对应文件，执行巡检。
2. 确认返回 `missingFileExportIds` 包含该任务。
3. 执行 `repair=true` 后确认该任务状态转为 `FAILED`。
4. 在导出目录放入未被任务引用文件，确认可识别并删除。
