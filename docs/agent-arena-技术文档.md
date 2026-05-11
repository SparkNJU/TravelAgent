# Agent 对比与排行榜技术文档

## 1. 功能目标

本模块实现两类核心能力：

- Agent 对比（Arena Mode）
  - 通过输入框旁的独立“竞技场模式”按钮进入随机对比模式。
  - 从候选模型池随机抽取两个模型，匿名映射为 A/B。
  - 并行生成两份回答，用户在不知模型身份的前提下进行投票。
  - 投票后揭晓模型真实名称。

- 模型排行榜（Leaderboard）
  - 汇总历史对战投票结果。
  - 计算每个模型的对战评分并展示完整排名。
  

## 2. 前后端架构

### 2.1 前端关键模块

- AI 对话页
  - 文件：frontend/src/views/AIPlanView.vue
  - 职责：发起 Auto 对比请求、维护对比阶段状态、提交投票。

- 对比卡片
  - 文件：frontend/src/components/ai-plan/ModelArenaCompare.vue
  - 职责：匿名展示 A/B 回答、投票交互、投票后揭晓模型、全屏单栏/双栏查看（支持同步滚动）。

- 对比时间线
  - 文件：frontend/src/components/ai-plan/ArenaTimeline.vue
  - 职责：以可折叠时间线展示阶段状态（等待中/执行中/完成/失败）和阶段详情。

- 排行榜页面
  - 文件：frontend/src/views/ModelLeaderboardView.vue
  - 文件：frontend/src/components/ModelLeaderboardPanel.vue
  - 职责：拉取并渲染全部模型评分、胜负平统计详情。

### 2.2 后端关键模块

- 对比控制器
  - 文件：backend/src/main/java/org/example/backend/controller/ModelArenaController.java
  - 接口：
    - POST /api/arena/auto
    - POST /api/arena/auto/stream
    - POST /api/arena/vote
    - GET /api/arena/leaderboard

- 对比服务
  - 文件：backend/src/main/java/org/example/backend/service/ModelArenaService.java
  - 职责：随机抽取模型、并行调用双模型回答、输出实时 SSE 事件、落库投票、生成排行榜。

- Agent 调用服务
  - 文件：backend/src/main/java/org/example/backend/service/TripAssistantService.java
  - 职责：调用 Python Agent SSE 接口；支持同步聚合答案和流式事件回调两种模式。

## 3. Agent 对比流程

### 3.1 交互流程

1. 用户点击输入框旁的“竞技场模式”按钮后提交问题。
2. 前端创建匿名对比消息，初始化阶段时间线：
   - 匿名模型抽取
   - 并行请求派发
   - 模型思考与草拟
   - 结果整理与匿名展示
3. 后端随机抽取两个模型并并发调用，建立双路 SSE 事件流。
4. 前端按事件类型实时更新：阶段状态、模型事件、回答分片。
5. 双路完成后进入匿名投票阶段；投票后显示真实模型名称。

### 3.2 匿名机制

- 投票前：
  - 仅显示“模型 A / 模型 B”。
  - 不展示真实 model name。
- 投票后：
  - 使用真实 modelA/modelB 替换匿名标签。

### 3.3 阶段时间线数据结构

前端在 arena 消息中维护 stages 数组，每一项包含：

- id：阶段唯一标识
- title：阶段名称
- status：pending/running/done/error
- time：阶段时间标签（HH:mm:ss）
- expanded：是否展开详情
- detail：阶段说明（由真实事件持续刷新，展示模型事件流）

### 3.4 SSE 事件协议（/api/arena/auto/stream）

前端通过 POST + text/event-stream 消费 arena 事件。主要事件类型：

- arena_init：双模型对比任务已创建
- arena_model_event：模型事件（thought/action/observation/reflection/plan）
- arena_answer_chunk：回答分片（source=A|B）
- arena_model_done：单模型输出结束
- arena_model_error：单模型流式调用异常
- arena_complete：双路输出完成，携带 modelA/modelB/answerA/answerB
- arena_error：整体对比流程错误

## 4. 全屏查看与并排对比

对比卡片支持两种全屏查看模式：

- 每个回答列（A/B）提供全屏按钮。
- 单栏模式：在 A/B 之间切换。
- 双栏模式：A/B 并排展示。
- 双栏同步滚动：任一侧滚动时另一侧自动同步到同位置。
- 适用于长回答精读、结构对照和细节比对。

## 5. 排行榜计算逻辑

### 5.1 数据来源

- 投票表：model_arena_votes
- 字段：model_a, model_b, result（A/B/BOTH_GOOD/BOTH_BAD）

### 5.2 统计口径

- 胜/负/平：按结果累加到各模型。
- 对战场次：每条投票对双方场次 +1。
- 排行榜展示不再限制前 5 名，服务返回的全部条目都会渲染到前端。

### 5.3 评分计算

当前服务端使用成对对战统计与 Bradley-Terry 迭代思路，得到每个模型强度值，并线性缩放为可读分值区间用于展示排序。

## 6. 性能与时延优化建议

目标：在保证回答质量与充分生成时间的前提下，降低平均等待时间并提升稳定性。

### 6.1 已采用策略

- 双模型并发生成（CompletableFuture 并发）
- 独立 arena 线程池（减少默认公共线程池争抢）
- 实时 SSE 双路事件流（替代定时模拟）
- 前端阶段时间线（由真实事件驱动）
- 排行榜不做前端截断，直接渲染后端返回的全部条目

## 7. 可维护性建议

- 将对比阶段定义抽离为常量配置，便于迭代。
- 前端 stages 字段与后端事件结构可逐步对齐，后续可升级为真正流式阶段回放。
- 为排行榜计算补充回归测试，确保评分逻辑调整不影响历史可比性。

## 8. 已知限制

- 当前“模型思考内容”来自事件流摘要，不直接暴露模型内部 chain-of-thought。
- /api/arena/auto（同步接口）仍保留，用于降级或兼容旧流程。
- 当前 SSE 聚合在应用层进行；如需更高吞吐可引入消息队列或专门流处理层。
