# Step 9 交付物：运行对比页（同任务不同 run）（2026-04-25）

## 1. 本步目标

1. 支持同一任务下两个 run 的指标差异对比。
2. 支持样本级差异对比，定位输出变化与错误变化。
3. 前端在运行详情区域提供 baseline/target 选择与一键对比。

## 2. 后端改造

### 2.1 新增 DTO

1. `backend/src/main/java/com/tripagent/backend/dto/eval/RunCompareResponse.java`
2. `backend/src/main/java/com/tripagent/backend/dto/eval/RunMetricDiffResponse.java`
3. `backend/src/main/java/com/tripagent/backend/dto/eval/RunSampleDiffResponse.java`

### 2.2 新增接口

1. `GET /api/eval/tasks/{taskId}/runs/compare?baselineRunId={id}&targetRunId={id}`

返回内容：

1. 对比任务和 run 信息
2. 指标差异列表（baseline/target/delta）
3. 样本差异列表（输出/错误变化）

### 2.3 运行服务能力

变更文件：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java`

关键实现：

1. 校验两个 run 均属于指定 task。
2. 对比核心指标：完成率、工具正确性、效率、P95、Token、三维得分。
3. 对比样本结果：actualOutput 与 errorMessage。
4. 返回变化样本数 `changedSamples`。

## 3. 前端改造

变更文件：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`
3. `frontend/src/style.css`

关键变化：

1. 新增 `compareTaskRuns` API。
2. 运行详情中新增 baseline/target run 选择器。
3. 展示指标差异表与样本差异表。
4. 结果摘要显示变化样本数量。

## 4. 验证建议

1. 后端编译：`mvn -DskipTests compile`
2. 前端构建：`npm.cmd run build`
3. 手工验证：
- 在同一 task 下执行至少 2 次 run
- 选择两次 run 执行对比
- 检查指标差值与样本差异是否符合预期
