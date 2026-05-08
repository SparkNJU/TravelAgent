# Step 7 交付物：真实数据集解析 + 任务运行历史查询（2026-04-24）

## 1. 本步目标

1. 执行评测时不再使用内置硬编码样本，改为按 datasetId 解析资源文件。
2. 补齐任务与运行的关系查询接口，前端可按任务查看历史运行。
3. 前端任务列表页不依赖本地 run 映射，刷新后仍可查看最新运行详情。

## 2. 后端改造内容

### 2.1 数据集解析服务

新增文件：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalDatasetSample.java`
2. `backend/src/main/java/com/tripagent/backend/service/eval/EvalDatasetLoaderService.java`

能力说明：

1. 支持 `classpath:datasets/{datasetId}.json`。
2. 支持 `classpath:datasets/{datasetId}.csv`。
3. 自动归一化字段：`id`、`input`、`expectedOutput/expected/expected_output`。
4. 解析失败或文件缺失时抛出明确异常信息。

### 2.2 运行执行改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java`

关键变化：

1. `executeRunAsync` 中样本来源改为 `EvalDatasetLoaderService.loadSamples(datasetId)`。
2. 删除旧的硬编码样本构造逻辑。
3. 新增 `listRunsByTaskId(taskId)`，用于查询任务下所有运行（按 runId 倒序）。

### 2.3 任务-运行查询接口

变更文件：

1. `backend/src/main/java/com/tripagent/backend/repository/EvalRunRepository.java`
2. `backend/src/main/java/com/tripagent/backend/service/eval/EvalTaskService.java`
3. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`

新增接口：

1. `GET /api/eval/tasks/{taskId}/runs`

说明：

1. 返回该任务的运行列表（新到旧），可用于前端“详情”入口与历史浏览。

## 3. 前端改造内容

变更文件：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`

关键变化：

1. 新增 `listTaskRuns(taskId)` API。
2. 任务列表加载后并发查询每个任务的最新运行，回填 `runByTask` 与 `runIdByTask`。
3. 点击“详情”时若本地没有 runId，会先向后端拉取任务运行历史再进入详情。
4. 刷新页面后仍能查看已有运行记录，不要求先点击“启动”。

## 4. 数据集示例

新增资源文件：

1. `backend/src/main/resources/datasets/dataset-trip-001.json`
2. `backend/src/main/resources/datasets/dataset-trip-002.csv`

## 5. 验证建议

1. 后端编译：`./mvnw -DskipTests compile`
2. 前端构建：`npm.cmd run build`
3. 手工验证：
- 创建任务时使用 `dataset-trip-001` 或 `dataset-trip-002`
- 执行任务后刷新页面，直接点击“详情”可进入最新 run
