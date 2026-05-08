# Step 11 交付物：任意运行对比与指标排序高亮（2026-04-25）

## 1. 本步目标

1. 运行对比不再受“当前分页”限制，支持手动输入任意 `runId`。
2. 指标差异支持按 `delta` 升序/降序排序。
3. 指标差异列增加正负高亮，便于快速识别波动方向。

## 2. 前端改造

变更文件：

1. `frontend/src/views/DashboardView.vue`
2. `frontend/src/style.css`

能力新增：

1. 对比面板新增：
- `manualBaselineRunId`
- `manualTargetRunId`

2. 对比参数解析规则：
- 若填写手动 `runId`，优先使用手动值。
- 未填写时回退到当前分页下拉选择值。

3. 指标差异排序：
- 新增排序模式 `none / deltaDesc / deltaAsc`。
- 通过 `sortedMetricDiffs` 计算属性输出表格展示顺序。

4. 指标差异高亮：
- `delta > 0` 使用 `delta-up`。
- `delta < 0` 使用 `delta-down`。

## 3. 验证建议

1. 进入任务详情，切到某任务运行历史页。
2. baseline/target 分别使用：
- 一个来自当前分页下拉
- 一个来自手动输入 runId
3. 点击“对比”，确认结果可返回。
4. 切换排序模式，确认指标顺序发生变化。
5. 检查 delta 文本颜色，确认正负高亮生效。
