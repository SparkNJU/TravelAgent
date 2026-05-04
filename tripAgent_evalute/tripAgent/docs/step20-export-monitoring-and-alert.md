# Step 20 交付物：导出任务监控统计与告警（2026-04-27）

## 1. 本步目标

1. 提供导出任务运行统计视图（成功率、失败率、重试率等）。
2. 提供简单阈值告警输出，便于快速发现异常趋势。
3. 支持按时间窗口统计。

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/dto/eval/ExportMonitorMetricsResponse.java`
2. `backend/src/main/java/com/tripagent/backend/repository/EvalExportTaskRepository.java`
3. `backend/src/main/java/com/tripagent/backend/service/eval/EvalExportService.java`
4. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`
5. `backend/src/main/resources/application.yml`

新增接口：

1. `GET /api/eval/exports/metrics?hours=24`

返回内容：

1. 状态统计：`total/succeeded/failed/pending/running`
2. 行为统计：`retried/cleanedUp/deleted/consistencyRepaired`
3. 比率统计：`successRate/failureRate/retryRate`
4. 告警列表：`alerts`

配置项：

1. `eval.export.monitor.failure-rate-alert-threshold`
2. `eval.export.monitor.retry-rate-alert-threshold`
3. `eval.export.monitor.pending-alert-minutes`

## 3. 验证建议

1. 创建多条导出任务并构造部分失败任务。
2. 调用 metrics 接口，确认统计值与实际任务一致。
3. 调整阈值后再次调用，确认 `alerts` 变化符合预期。
