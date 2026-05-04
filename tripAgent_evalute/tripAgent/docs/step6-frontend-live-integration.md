# Step 6 交付物：前端真实联调（2026-04-24）

## 1. 目标

将评测控制台从静态演示升级为真实后端联调页面，覆盖任务、运行、策略与自定义指标主流程。

## 2. 本次实现内容

1. API 客户端升级
- 新增任务、运行、记录、指标、策略、策略版本、自定义指标的类型与请求方法
- 新增运行 SSE 连接方法

2. 任务看板联调
- 任务列表从后端加载
- 状态/版本筛选与关键字过滤
- 创建任务、启动任务
- 运行详情加载（records + metrics）

3. 运行监控联调
- 对接 `/api/eval/runs/{runId}/stream`
- 时间线显示 `run_started/sample_start/sample_done/strategy_applied/run_done/error`
- 自动刷新当前运行数据

4. 策略与指标配置联调
- 创建策略
- 创建策略版本
- 注册自定义指标
- 拉取策略与自定义指标列表

5. 监控面板组件改造
- `ChatPanel` 改为 props 驱动（timeline/traces/errorSummary）
- 支持刷新事件回调

## 3. 主要代码文件

1. `frontend/src/api/client.ts`
2. `frontend/src/views/DashboardView.vue`
3. `frontend/src/components/ChatPanel.vue`
4. `frontend/src/style.css`

## 4. 验证结果

1. 前端构建通过：`npm.cmd run build`
2. 页面已具备真实接口调用能力，可进入端到端联调阶段。

## 5. 后续建议

1. 将 runId 与 taskId 绑定关系落到后端查询接口，避免前端仅存会话态映射。
2. 增加 records/metrics 自动轮询兜底，提升网络抖动场景可观测性。
3. 增加失败重试按钮与任务停止能力。
