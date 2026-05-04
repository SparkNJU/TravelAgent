# Step 10 交付物：对比导出 + 变化样本筛选（2026-04-25）

## 1. 本步目标

1. 运行对比结果支持导出（JSON / CSV）。
2. 运行对比支持“仅显示变化样本”开关。
3. 前后端参数对齐，筛选条件可直接作用于对比结果。

## 2. 后端改造

变更文件：

1. `backend/src/main/java/com/tripagent/backend/controller/eval/EvalTaskController.java`
2. `backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java`

接口升级：

1. `GET /api/eval/tasks/{taskId}/runs/compare`

新增参数：

1. `changedOnly`（默认 true）

行为：

1. `changedOnly=true` 仅返回变化样本。
2. `changedOnly=false` 返回全部样本并带 `changed` 标记。

## 3. 前端改造

变更文件：

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`
3. `frontend/src/style.css`

能力新增：

1. 对比参数增加 `changedOnly`。
2. 对比面板新增复选框“仅显示变化样本”。
3. 新增导出按钮：
- 导出 JSON
- 导出 CSV

实现说明：

1. 前端基于当前对比结果生成下载文件，不依赖新增后端导出接口。
2. CSV 包含指标差异段与样本差异段，便于表格工具二次分析。

## 4. 验证建议

1. 后端编译：`mvn -DskipTests compile`
2. 前端构建：`npm.cmd run build`
3. 手工验证：
- 选择 baseline/target 并执行对比
- 切换 `changedOnly` 开关后重新对比
- 点击 JSON/CSV 导出并检查文件内容
