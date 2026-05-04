# Step 8 交付物：任务运行历史分页与状态筛选（2026-04-25）

## 1. 本步目标

1. 任务运行历史查询支持分页，避免任务历史过长时一次返回过多数据。
2. 支持按运行状态筛选（RUNNING/SUCCEEDED/FAILED）。
3. 前端看板提供运行历史筛选和翻页入口，可快速切换历史 run 详情。

## 2. 后端改造

### 2.1 分页返回结构

新增 DTO：

1. `backend/src/main/java/com/tripagent/backend/dto/eval/TaskRunsPageResponse.java`

字段：

1. `items`
2. `page`
3. `size`
4. `total`
5. `totalPages`
6. `hasNext`

### 2.2 Repository

变更：

1. `backend/src/main/java/com/tripagent/backend/repository/EvalRunRepository.java`

新增能力：

1. `findByTaskTaskId(taskId, pageable)`
2. `findByTaskTaskIdAndStatus(taskId, status, pageable)`

### 2.3 Service

变更：

1. `backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java`
2. `backend/src/main/java/com/tripagent/backend/service/eval/EvalTaskService.java`

新增能力：

1. 解析运行状态过滤参数（非法值报错）。
2. `listRunsByTaskIdPaged(taskId, status, page, size)` 分页查询。
3. 控制 page/size 安全范围（page >= 0，size 限制 1~100）。

### 2.4 Controller

变更：

1. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`

接口：

1. `GET /api/eval/tasks/{taskId}/runs?status=&page=0&size=20`

返回：

1. `TaskRunsPageResponse`

## 3. 前端改造

变更：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`
3. `frontend/src/style.css`

关键变化：

1. `listTaskRuns` 从数组改为分页对象返回。
2. 任务列表同步最新 run 时改为拉取 `size=1` 的第一页。
3. 运行详情区新增：
- 运行状态筛选
- 上一页/下一页
- 当前任务运行历史列表
4. 点击历史 run 可以直接切换当前详情视图。

## 4. 验证建议

1. 后端编译：`mvn -DskipTests compile`
2. 前端构建：`npm.cmd run build`
3. 手工联调：
- 针对同一 task 多次启动，产生多个 run
- 切换筛选状态并翻页
- 点击历史 run，确认 records/metrics 正常刷新
