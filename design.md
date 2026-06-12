# TravelMind 前端设计优化方案

更新时间：2026-06-10

## 1. 目标与边界

本方案面向当前项目的 `frontend`，主要覆盖首页、发现、AI规划、模型排行、规划工作台、设置/我的等前端界面。`evaluator` 目录不纳入本轮设计与实现范围。

本轮目标不是推翻现有产品，而是在保留当前红白主品牌、旅行规划业务和已有功能的前提下，把界面统一成一个更成熟的 AI Agent 旅行规划产品。

核心原则：

- 功能优先：远程 `origin/master` 已有功能必须保留。
- 前端优化其次：优化只在功能稳定基础上进行。
- 红白主视觉：保持当前项目白底、红色强调色，不采用深色主体。
- 首页特殊化：首页不显示侧边栏，作为品牌入口和地球探索入口。
- 工作区产品化：AI规划、排行榜、工作台要从“页面堆叠”升级为“任务工作台”。

## 2. 参考产品与设计归纳

参考对象不是视觉照搬，而是提取主流 AI/Agent 产品的信息组织方式。

| 产品 | 可借鉴点 | 对 TravelMind 的转译 |
| --- | --- | --- |
| ChatGPT | 简洁入口、对话优先、低干扰输入 | 首页保持强入口；AI规划减少无关装饰，让输入和结果成为中心 |
| Claude | 左历史、中对话、右上下文/工具的工作台感 | AI规划拆分历史、消息、上下文和结果，降低输入栏拥挤度 |
| Perplexity | 搜索优先、结果有来源感、信息密度克制 | 发现页强化搜索、标签、来源和“用它规划”的明确动作 |
| Gemini / AI Studio | 模型选择、参数控制、结果区清晰 | AI规划和排行榜的模型控制更显式，但收纳高级项 |
| LMSYS / Arena 类榜单 | 左侧筛选、右侧排名、指标切换 | 模型排行保留 arena 式结构，但改成红白主题和旅行产品语境 |

参考入口：

- ChatGPT: https://chatgpt.com/
- Claude: https://claude.ai/
- Perplexity: https://www.perplexity.ai/
- Gemini: https://gemini.google.com/
- Google AI Studio: https://aistudio.google.com/
- LM Arena: https://lmarena.ai/

## 3. 设计方向

设计概念：红白旅行 AI 指挥台。

关键词：

- Refined utility：精致但克制，像专业工具，不像营销落地页。
- Travel signal：保留旅行感，使用地图、地点、路线、日期、目的地作为核心视觉语言。
- Agent workflow：突出 AI 正在规划、搜索、确认、生成、落地到工作台的流程。
- Dense but calm：信息密度可以高，但层级要清楚，不能杂乱。

不采用：

- 大面积深色背景。
- 大面积紫色/蓝紫渐变。
- 多层卡片套卡片。
- 装饰性光球、漂浮渐变球、无意义插画。
- 只为“科技感”加入复杂动效。

## 4. 当前主要问题

### 4.1 全局统一性不足

当前多个页面仍然硬编码颜色和布局，例如排行榜、发现页、工作台大量使用固定 `#fafafa`、`#ffffff`、`#6366f1`、`#8b5cf6` 等颜色，和 `variables.css` 的主题变量没有完全统一。

影响：

- 深浅色切换不彻底。
- 红白品牌被局部紫色/蓝色打断。
- 页面之间像不同产品拼接。

### 4.2 页面壳体不一致

首页是全屏地球，其他页面是左侧导航；AI规划页内部又有自己的历史栏；工作台则是独立地图编辑器。整体可接受，但需要统一“页面标题、工具区、内容区”的结构语言。

### 4.3 首页还依赖手工调参

地球、标题、按钮、提示之间使用固定上边距和视口高度，容易在不同屏幕上出现遮挡、比例失衡或空白过大。

### 4.4 AI规划输入区过度拥挤

上传、模式、模型、竞技场、上下文压缩、快捷标签都集中在输入框 footer，导致视觉重点不明确，也不利于移动端。

### 4.5 排行榜仍偏普通表格

左筛选和右表格已经接近 arena 结构，但桌面端还缺少榜单节奏，移动端横向表格体验较弱。

### 4.6 工作台色彩与产品主色不统一

工作台仍有明显蓝紫色调，和当前红白产品方向不一致。右侧编辑器固定宽度也会挤压地图。

## 5. 全局设计规范

### 5.1 颜色

保留当前红色为主品牌：

- 主红：`#ff2442`
- 轻红背景：`#fff1f3`
- 页面背景：`#fafafa`
- 卡片背景：`#ffffff`
- 主文字：`#1f1f1f`
- 次级文字：`#666666`
- 边框：`#eeeeee`

需要统一到 CSS 变量：

- 所有页面背景使用 `var(--color-bg)` 或新增 `--color-page`。
- 所有卡片使用 `var(--color-card)`。
- 所有边框使用 `var(--color-border)`。
- 只在明确语义场景使用非红色，例如成功绿、警告橙。

### 5.2 圆角

当前圆角偏大，需要收敛：

- 工具按钮：8px 或 pill。
- 卡片：8px 到 10px。
- 弹窗：16px。
- 头像、标签可以使用 pill。

### 5.3 阴影

阴影只表达层级，不制造漂浮感。

- 普通卡片：极轻阴影或无阴影。
- 悬浮卡片：轻阴影。
- 弹窗/浮层：中等阴影。
- 地球首页允许特殊光效，但不能影响可读性。

### 5.4 字体与排版

保持中文可读优先：

- 正文继续使用现有中文字体栈。
- 标题减少过大字号，首页可以大，但非首页不使用 hero 级标题。
- 数字列、排名、日期、价格可使用等宽字体栈。
- 不使用负 letter-spacing。

### 5.5 动效

只保留任务反馈型动效：

- 按钮 hover 位移控制在 1-2px。
- 流式输出使用轻量 loading 和阶段状态。
- 首页地球自转保留。
- 列表、卡片不做过度弹跳。

## 6. 页面级方案

## 6.1 首页

定位：产品入口 + 地球探索入口。

结构：

- 首页不显示侧边栏。
- 上半部分：短标语、辅助描述、两个按钮。
- 中心：透明边框地球，大陆/国界线/红点/地名随地球旋转。
- 下方：细线或微弱地平线，减少空白。

建议文案：

- 主标题：`Go Anywhere You Want`
- 副文案：`点击地球上的城市，让 AI 生成你的下一段旅行计划。`
- 主按钮：`探索`
- 次按钮：`AI规划`

设计调整：

- 地球区块使用 `clamp()` 管理高度，不再单靠固定 `vh`。
- 标题与地球使用同一垂直节奏，避免遮挡。
- 标签增加可见性判断与基本避让，减少重叠。
- 首页背景仍可偏暗，以突出地球，但其他页面保持白底。

涉及文件：

- `frontend/src/views/HomeView.vue`
- `frontend/src/components/HomeGlobeHero.vue`

## 6.2 左侧导航

定位：非首页的主导航。

结构：

- 首页不显示侧边栏。
- 非首页显示侧边栏。
- 主导航项：发现、AI规划、发布笔记、模型排行。
- `我的` 收进底部头像卡片点击后的菜单。
- 设置可放头像菜单或底部小入口，不和主任务并列。
- 主题切换放最底部。

设计调整：

- 侧边栏宽度控制在 184px 到 198px。
- 移动端收缩为图标栏。
- active 状态统一红色背景。
- 发布笔记作为动作按钮，不作为普通页面链接。

涉及文件：

- `frontend/src/App.vue`
- `frontend/src/components/AppNavigation.vue`

## 6.3 发现页

定位：旅行灵感流，承担从社区内容到 AI 规划的转化。

结构：

- 顶部：页面标题 + 搜索框。
- 第二行：频道筛选 chips + 发布入口。
- 内容：稳定瀑布流或响应式 grid。
- 卡片：封面、城市、标题、摘要、标签、作者、点赞收藏、`用它规划`。
- 详情弹窗：左图片右内容，移动端上下布局。

设计调整：

- 搜索优先，降低标题的营销感。
- 卡片圆角收敛，统一 10px。
- `用它规划` 按钮始终清晰。
- 标签颜色统一红色。
- 发现页保持浅色，不使用首页暗色。

涉及文件：

- `frontend/src/views/CommunityView.vue`
- `frontend/src/components/PublishModal.vue`

## 6.4 AI规划页

定位：核心 Agent 工作台。

目标结构：

- 左侧：对话历史，可折叠，折叠态仍有明确展开按钮。
- 中间：对话消息流、计划流、工具事件。
- 右侧：上下文/模型/记忆/结果摘要，可在窄屏收起。
- 底部：输入框。

输入区重构：

- 第一层：文本输入 + 发送/停止。
- 第二层：上传、模式、模型。
- 高级项：竞技场、上下文压缩、token 状态进入更多面板。
- 快捷标签只在空会话展示，进入正式对话后隐藏或折叠。

消息区重构：

- 用户消息保持右侧红色气泡。
- AI 回答保持左侧白底正文。
- 执行计划 `AgentPlanBlock` 变为轻量可折叠任务面板。
- 工具事件按 `思考 / 动作 / 观察 / 反思` 分组展示，默认收起低价值细节。
- 最终结果突出 `进入可视化工作台`。

涉及文件：

- `frontend/src/views/AIPlanView.vue`
- `frontend/src/components/ai-plan/ChatInput.vue`
- `frontend/src/components/ai-plan/ConversationSidebar.vue`
- `frontend/src/components/ai-plan/MessageBubble.vue`
- `frontend/src/components/ai-plan/AgentPlanBlock.vue`
- `frontend/src/components/ai-plan/AgentEventBlock.vue`
- `frontend/src/components/ai-plan/StreamingIndicator.vue`
- `frontend/src/components/ai-plan/ContextPanel.vue`

## 6.5 模型排行榜

定位：红白版 arena 排行榜。

目标结构：

- 左侧：指标筛选、厂商筛选、可信度筛选。
- 右侧顶部：标题、当前指标、投票入口、搜索、刷新。
- 主区：榜单表格。
- 移动端：改为模型排行卡片，不强迫横向滚动。

指标：

- Overall
- Win Rate
- Votes
- Confidence
- Cost Efficiency
- Context

设计调整：

- 左侧筛选固定宽度，折叠后只显示图标和当前指标。
- 表格列更接近 arena：Rank、Rank Spread、Model、Score、Votes、Price、Context。
- 去除蓝紫色强调，全部使用红色 active。
- Preliminary、High、Medium、Low 使用轻量 badge。
- 表格行 hover 使用浅红背景。

涉及文件：

- `frontend/src/views/ModelLeaderboardView.vue`
- `frontend/src/components/ModelLeaderboardPanel.vue`

## 6.6 规划工作台

定位：地图为主的行程编辑器。

结构：

- 顶部：返回、标题、目的地、保存状态。
- 第二行：天数轴。
- 主区：地图全屏为主。
- 右侧：当天行程编辑器，可收起。
- 无选择状态：地图上轻提示。

设计调整：

- 替换蓝紫主色为红色/中性色。
- 右侧编辑器宽度降低到 380px 左右，或支持折叠。
- 移动端右侧编辑器作为底部抽屉。
- 地图 marker 与天数颜色保留多色，但红色作为当前选中主色。
- 加强保存状态反馈，但不要占太多空间。

涉及文件：

- `frontend/src/views/PlanWorkbenchView.vue`
- `frontend/src/components/MapComponent.vue`

## 6.7 设置与我的

定位：用户身份、技能、记忆、个人资料管理。

结构：

- `我的`：个人信息、历史规划、发布内容。
- `设置`：技能、记忆、主题、偏好。
- 技能和记忆保持双栏，但移动端单栏。

设计调整：

- 当前设置页功能丰富，但文字和卡片层级偏重，需要统一红白变量。
- 技能卡、记忆卡减少装饰色，使用图标 + 状态 + 操作。
- 弹窗表单保持宽松，但按钮统一主红。

涉及文件：

- `frontend/src/views/ProfileView.vue`
- `frontend/src/views/SettingsView.vue`

## 7. 实施顺序

### Step 1：统一设计变量与全局壳

目标：

- 收敛 `variables.css`。
- 清理页面硬编码颜色。
- 稳定首页不显示侧边栏、其他页面显示侧边栏。

文件：

- `frontend/src/assets/variables.css`
- `frontend/src/assets/main.css`
- `frontend/src/App.vue`
- `frontend/src/components/AppNavigation.vue`

验收：

- 首页全屏无侧栏。
- 发现、AI规划、排行榜、工作台显示一致导航结构。
- 主色统一为红白。

### Step 2：首页视觉校准

目标：

- 地球不遮挡。
- 字体比例合理。
- 红点、地名、国界线清晰。

文件：

- `frontend/src/views/HomeView.vue`
- `frontend/src/components/HomeGlobeHero.vue`

验收：

- 桌面 1440x900、移动 390x844 无遮挡。
- 地球主体可见，标签不大面积重叠。
- 点击红点进入 AI 规划并携带 query。

### Step 3：AI规划工作台优化

目标：

- 输入区拆层。
- 历史栏折叠态可恢复。
- 消息、计划、工具事件层级更清楚。

文件：

- `frontend/src/views/AIPlanView.vue`
- `frontend/src/components/ai-plan/*`

验收：

- 空会话输入清晰。
- 有消息时底部输入不拥挤。
- 流式输出过程可读。
- 上下文压缩入口明确但不干扰主流程。

### Step 4：排行榜红白 arena 化

目标：

- 保留 arena 左筛选右榜单结构。
- 移动端卡片化。
- 不使用深色主体。

文件：

- `frontend/src/views/ModelLeaderboardView.vue`
- `frontend/src/components/ModelLeaderboardPanel.vue`

验收：

- 指标切换能改变排序。
- 左侧筛选可折叠。
- 桌面表格密度合理。
- 移动端无需横向滚动即可阅读核心信息。

### Step 5：发现页和工作台收尾

目标：

- 发现页内容卡片更稳定。
- 工作台去蓝紫化，地图优先。

文件：

- `frontend/src/views/CommunityView.vue`
- `frontend/src/views/PlanWorkbenchView.vue`
- `frontend/src/components/MapComponent.vue`

验收：

- 发现页卡片、弹窗、发布入口统一。
- 工作台地图不被编辑器过度挤压。
- 自动保存状态清楚。

### Step 6：响应式与验证

目标：

- 桌面、平板、移动端都可用。
- 无明显遮挡、横向溢出、文字溢出。

建议检查视口：

- 1440x900
- 1280x720
- 1024x768
- 390x844
- 375x667

命令：

```powershell
cd frontend
npm run build
```

如果需要本地检查：

```powershell
cd frontend
npm run dev
```

## 8. 验收标准

功能验收：

- 首页点击探索进入发现页。
- 首页点击地球红点进入 AI规划并自动带目的地 query。
- 发现页发布笔记仍要求登录。
- AI规划对话、流式输出、停止、压缩、竞技场功能仍可用。
- 排行榜接口 `/api/arena/leaderboard` 正常展示。
- 工作台可从对话结果进入，可编辑并自动保存。

视觉验收：

- 非首页统一红白浅色主体。
- 首页地球无遮挡。
- 排行榜不是深色。
- 不出现明显紫蓝主色抢占品牌。
- 文本不溢出按钮或卡片。
- 移动端不出现不可控横向滚动。

代码验收：

- 不引入新 UI 框架。
- 优先复用现有 Vue 组件。
- 样式尽量通过变量统一。
- 不改动 evaluator。
- 不进行 git commit。

## 9. 风险与注意事项

- 当前本地分支落后 `origin/master`，但工作区已经合并了远程功能文件。改前端时要避免误删远程新增功能。
- `Sidebar.vue` 已被新导航替代，当前删除状态合理，但如需完全贴合远程文件树可保留未引用文件。
- 首页地球依赖 `frontend/public/data/*.geojson`，这些是真实资产，不应删除或忽略。
- AI规划页和工作台涉及后端接口，视觉改动不能破坏请求参数。
- `.env` 本地文件已被忽略，不应提交。

## 10. 建议下一步

先执行 Step 1 和 Step 2：统一变量、导航壳、首页地球。这样可以快速解决当前最明显的视觉不统一和首页遮挡问题，再进入 AI规划和排行榜这两个高复杂页面。

## 11. 本轮专项方案：AI规划工作台、上下文面板与 Agent 工具侧栏

更新时间：2026-06-10

本轮只制定方案，下一步再执行代码修改。范围优先限制在 `frontend`，尽量不改 `backend` 与 `agent`。目标是解决 AI规划页当前的可用性问题，同时把远程分支新增的 skill、memory、联网搜索能力纳入统一的 Agent 控制体验。

### 11.1 设计参考与产品方向

参考对象：

- 用户提供的 Gemini / Google AI Studio 侧栏截图：左侧是清晰的产品导航，右侧 `Run settings` 以分组卡片组织模型、工具、联网搜索等运行参数。
- `origin/master` 中上下文弹窗样式：上下文占用百分比、健康状态、token 统计、压缩按钮集中在一个轻量浮层中。
- Claude / ChatGPT 类 Agent 工作台模式：主对话区保持专注，工具、记忆、上下文、技能等高级能力放进可收纳的侧面控制区。

TravelMind 的转译方向：

- 保持红白浅色主体，不照搬 Gemini 的灰白产品色，而是采用 `Travel Agent Control Rail`：左侧主导航 + 右侧 Agent 工具抽屉。
- AI规划页中心必须优先保证“对话可读、输入无遮挡、上下文状态清晰”。
- skill、memory、联网搜索不再散落在设置页或隐藏入口，而是成为 Agent 工作台的直接能力开关。

### 11.2 问题拆解

#### 问题 A：折叠历史栏有重复展开入口

当前 `ConversationSidebar.vue` 折叠后同时存在：

- 顶部 `chevron-right` 展开按钮。
- 中部竖向虚线边框按钮 `展开历史`。

影响：

- 视觉重复。
- 窄侧栏被虚线按钮占用，显得像异常浮层。
- 与整体红白侧栏不够统一。

设计结论：

- 删除折叠态的 `collapsed-restore` 竖向按钮。
- 保留顶部 `chevron-right` 作为唯一展开入口。
- 折叠态只保留两类操作：展开历史、新建对话。
- 折叠态宽度保持 56px，不再出现额外文字。

涉及文件：

- `frontend/src/components/ai-plan/ConversationSidebar.vue`

验收：

- 折叠后不显示“展开历史”竖向按钮。
- 顶部 `>` 按钮可正常展开历史。
- 折叠栏没有虚线边框元素。

### 11.3 上下文面板重构方案

#### 当前问题

当前实现中有两套上下文表达：

- `AIPlanView.vue` 右侧 `plan-inspector` 中展示上下文百分比、状态、压缩按钮。
- `ChatInput.vue` 内部嵌入 `ContextPanel.vue`，点击后弹出上下文浮层。

实际表现问题：

- `ContextPanel` 浮层会遮挡底部输入框和消息内容。
- 当前右侧卡片的信息太粗，缺少 `origin/master` 里那种 token 明细和操作聚合。
- 输入区 footer 中按钮过多，上下文入口和竞技场、模型、模式混在一起。
- 在长回答场景下，底部输入栏和浮层容易挡住最后一段内容。

#### 目标形态

采用“双层上下文设计”：

- 输入栏里只保留一个小型上下文状态指示器：圆环或百分比小胶囊，只显示 `0% / 65% / 85%+`。
- 详细上下文内容统一放到右侧 `Agent Run Panel` 中，不再从输入栏弹大浮层。
- 如果用户点击输入栏里的上下文指示器，右侧面板自动滚动/切换到 `Context` 分组。

#### 右侧 Agent Run Panel

替换当前 `plan-inspector` 的视觉与信息结构，参考 Gemini `Run settings` 的分组方式：

顶部卡片：

- 标题：`Travel Agent`
- 副标题：当前会话运行环境。
- 当前状态：`空闲 / 思考中 / 调用工具 / 等待确认 / 已生成计划`

Run Settings 分组：

- 模式：`Agent / Plan / Reflection`
- 模型：当前选中模型
- 竞技场：开关状态
- 联网搜索：开关状态

Context 分组：

- 大号百分比：`Context 42%`
- 状态 badge：`健康 / 偏高 / 接近上限`
- 进度条
- 统计小格：
  - 历史对话 tokens
  - 输入估算 tokens
  - 输出预算 tokens
  - 最大上下文 tokens
- 主按钮：`压缩历史`
- 次级文案：最近一次压缩时间或压缩状态

Agent Assets 分组：

- 已启用技能数量
- 已启用记忆数量
- 快捷入口：`管理技能`、`管理记忆`

#### 输入区遮挡修复

对话区需要预留输入栏高度：

- `messages-area` 底部 padding 使用变量，例如 `--composer-reserved-height`。
- `compact-input-area` 固定在中间面板底部时，不覆盖最后一条消息。
- 有上下文详情时不在输入栏上方弹出大卡片，避免遮挡。

#### `ContextPanel.vue` 去留

两种选择：

- 推荐：把 `ContextPanel.vue` 改为纯小型指示器组件，例如 `ContextIndicator.vue`，不再内置大弹窗。
- 兼容：保留组件名，但删除 popup 结构，只 emit `open-context-panel`，由 `AIPlanView.vue` 控制右侧面板。

为了减少文件改动，下一步优先采用兼容方案。

涉及文件：

- `frontend/src/views/AIPlanView.vue`
- `frontend/src/components/ai-plan/ChatInput.vue`
- `frontend/src/components/ai-plan/ContextPanel.vue`

验收：

- 上下文信息不再遮挡输入框。
- 右侧面板展示的信息不少于 `origin/master` 的上下文弹窗。
- 点击上下文小指示器能定位到右侧上下文区域。
- 长回答滚动到底部时，最后一段内容不会被输入栏挡住。

### 11.4 全局侧栏 Agent 工具区设计

#### 当前问题

`AppNavigation.vue` 当前只有主业务导航：

- 发现
- AI规划
- 发布笔记
- 模型排行
- 头像/设置
- 深浅色切换

但项目已有能力包括：

- skill 管理：`/api/skills`，当前主要在 `SettingsView.vue` / `SkillStudioView.vue`。
- memory 管理：`/api/memories`，当前在 `SettingsView.vue`。
- web search / 联网搜索：工具事件中已有 `web_search` 表达，但前端没有清晰开关入口。

这些能力属于 Agent 的运行配置，不应该只藏在设置页里。

#### 新侧栏信息架构

保留主导航区：

- 发现
- AI规划
- 发布笔记
- 模型排行

新增 `Agent Tools` 分组，放在主导航下方、用户区上方：

- `Skills`：打开右侧技能管理抽屉。
- `Memory`：打开右侧记忆管理抽屉。
- `联网搜索`：开关项，显示当前是否开启。

视觉参考 Gemini：

- 分组标题使用小写灰色标签，例如 `AGENT`
- 每项左侧图标，右侧状态或 switch。
- `联网搜索` 使用 toggle，不跳转页面。
- `Skills / Memory` 使用按钮，不进入完整页面，而是打开右侧 drawer。

#### 折叠态表现

桌面展开态：

- 显示图标 + 文案 + 状态。
- `联网搜索` 显示小 switch。

移动/窄屏折叠态：

- 仅显示图标。
- hover/title 显示 `技能`、`记忆`、`联网搜索`。
- 搜索开启时图标使用红色点或浅红背景提示。

#### 用户菜单调整

头像下拉继续保留：

- 我的主页
- 设置
- 退出登录

但不再把 skill/memory 只放在设置中；设置页作为完整管理页保留，侧栏 drawer 作为高频快捷入口。

涉及文件：

- `frontend/src/components/AppNavigation.vue`
- `frontend/src/components/SvgIcon.vue`
- 可新增：`frontend/src/components/AgentToolDrawer.vue`
- 可新增：`frontend/src/composables/useAgentTools.js`

验收：

- 侧栏可直接打开技能管理抽屉。
- 侧栏可直接打开记忆管理抽屉。
- 侧栏可切换联网搜索开关，并在 AI规划页输入时保留状态。
- 首页仍不显示侧栏。

### 11.5 Skill / Memory 右侧抽屉设计

#### 入口方式

从主侧栏点击 `Skills` 或 `Memory`：

- 桌面端：右侧抽屉从屏幕右边滑入，宽度 440-520px。
- 移动端：底部抽屉，最大高度 86vh。
- 点击遮罩或关闭按钮退出。

#### 抽屉基础结构

顶部：

- 标题：`Agent Tools`
- Tabs：`Skills` / `Memory`
- 右侧关闭按钮

主体：

- 当前 tab 内容。
- 顶部提供搜索/筛选。
- 列表项保持紧凑，不做大卡片堆叠。

底部：

- 新建技能 / 添加记忆按钮。
- 状态提示：保存中、同步失败、已更新。

#### Skills tab

信息结构：

- 概览：`已启用 X / 总计 Y`
- 筛选：`全部 / 系统 / 自定义 / 已启用`
- 列表项：
  - 标题
  - `@name`
  - 描述一行截断
  - 启用 switch
  - 查看 / 编辑 / 删除

创建/编辑：

- 不使用居中大弹窗，优先在同一个右侧抽屉内切换到编辑表单。
- 表单字段沿用现有：
  - 技能名称
  - 唯一英文标识
  - 触发场景描述
  - 指令手册
  - 是否启用

API 复用：

- `GET /api/skills?userId=...`
- `PUT /api/skills/{id}/toggle?...`
- `POST /api/skills?userId=...`
- `PUT /api/skills/{id}?userId=...`
- `DELETE /api/skills/{id}?userId=...`

#### Memory tab

信息结构：

- 概览：`已启用 X / 总计 Y`
- 筛选：`全部 / 已启用 / 已停用`
- 列表项：
  - 记忆正文
  - 保存时间
  - 启用 switch
  - 编辑 / 删除

创建/编辑：

- 在抽屉内表单编辑。
- 字段：
  - 记忆内容
  - 是否启用

API 复用：

- `GET /api/memories?userId=...`
- `POST /api/memories?userId=...`
- `PUT /api/memories/{id}?userId=...`
- `DELETE /api/memories/{id}?userId=...`

#### 与现有 SettingsView 的关系

下一步实现时不删除 `SettingsView.vue`。

策略：

- 先新增抽屉组件，复用 `SettingsView.vue` 中已验证的请求逻辑。
- 设置页仍作为完整管理入口保留。
- 后续如需减少重复，再抽出 `SkillManagerPanel.vue` 与 `MemoryManagerPanel.vue` 供设置页和抽屉共用。

涉及文件：

- `frontend/src/views/SettingsView.vue`
- `frontend/src/views/SkillStudioView.vue`
- 新增：`frontend/src/components/AgentToolDrawer.vue`
- 可新增：`frontend/src/components/agent-tools/SkillManagerPanel.vue`
- 可新增：`frontend/src/components/agent-tools/MemoryManagerPanel.vue`

验收：

- 不进入设置页也能启停 skill。
- 不进入设置页也能增删改 memory。
- 抽屉关闭后不丢失主页面滚动位置和对话状态。
- 加载失败时显示明确错误，不影响主导航使用。

### 11.6 联网搜索开关设计

#### 入口

放在主侧栏 `Agent Tools` 分组内：

- 文案：`联网搜索`
- 图标：搜索/地球/雷达类图标
- 状态：toggle switch

AI规划页右侧 `Run Settings` 同步展示：

- `联网搜索：开启 / 关闭`
- 开启时展示轻提示：`规划时可调用 web_search 获取实时信息`

#### 状态管理

前端状态建议：

- 使用 `useAgentTools.js` 管理：
  - `webSearchEnabled`
  - `activeToolDrawer`
  - `skillsSummary`
  - `memoriesSummary`
- `webSearchEnabled` 存入 `localStorage`，key 可为 `travel_agent_web_search_enabled`。

请求接入策略：

- 如果后端已支持搜索开关参数，则 `AIPlanView.vue` 在发送对话时附带 `webSearchEnabled`。
- 如果后端当前忽略该字段，前端先保留状态与 UI，不破坏现有接口。
- 工具事件中如果出现 `web_search`，在 `AgentEventBlock.vue` 中以更明确的 `联网搜索` 标签展示。

验收：

- 开关状态刷新页面后保留。
- AI规划页右侧状态与侧栏开关同步。
- 关闭时 UI 不再暗示会使用联网搜索。
- 开启时工具事件文案清楚，不出现英文 `web_search` 直出给普通用户。

### 11.7 AI规划页版式重排

#### 桌面端

目标三栏：

- 左：全局导航 `AppNavigation`
- 次左：对话历史 `ConversationSidebar`
- 中：对话流 + 输入区
- 右：`Agent Run Panel`

宽度建议：

- 全局导航：当前变量 `--sidebar-width`
- 历史栏：展开 248px，折叠 56px
- 中间对话：`minmax(520px, 1fr)`
- 右侧面板：320-360px

#### 中间对话区

视觉规则：

- 用户气泡最大宽度 52-60%，靠右。
- Agent 事件块最大宽度 72-78%，靠左。
- 工具事件默认折叠成单行，关键结果可以展开。
- 最终计划结果用更宽的文档卡片展示，但不超过中心列。

#### 输入区

底部输入区拆成三层：

- 文本输入层。
- 左工具层：附件、对比、上下文指示器。
- 右运行层：模式、模型、发送。

优化点：

- 发送按钮固定最右。
- `立即压缩对话` 不再作为输入区浮层出现。
- 当右侧 `Context` 已经显示危险状态，输入区仅显示红色小提示点。

#### 窄屏适配

- 右侧 `Agent Run Panel` 默认收起为顶部/底部按钮。
- 点击上下文或工具入口时以底部 sheet 显示。
- 历史栏折叠为 56px 图标栏。
- 输入区 footer 自动换行，但发送按钮保持右下角。

涉及文件：

- `frontend/src/views/AIPlanView.vue`
- `frontend/src/components/ai-plan/ChatInput.vue`
- `frontend/src/components/ai-plan/ConversationSidebar.vue`
- `frontend/src/components/ai-plan/AgentEventBlock.vue`
- `frontend/src/components/ai-plan/MessageBubble.vue`

验收：

- 1440x900 下右侧面板不遮挡中心内容。
- 1280x720 下输入区不遮挡最后一条回答。
- 390x844 下可以正常输入、发送、展开工具面板。

### 11.8 视觉细则

颜色：

- 主色继续使用 `#ff2442`。
- Agent Tools 区不要引入大面积蓝紫。
- 状态色允许小面积使用：
  - 健康：中性黑/灰
  - 警告：橙
  - 危险：红

卡片：

- 右侧面板使用 `1px border + 轻阴影`，不套娃。
- 抽屉内列表项使用 8-10px 圆角。
- Toggle 使用红色 active，灰色 inactive。

图标：

- 优先使用现有 `SvgIcon`。
- 如果缺少图标，补充：
  - `brain`
  - `wrench`
  - `globe`
  - `panel-right`
  - `shield-check` 或 `sparkles`

动效：

- 右侧抽屉滑入时间 180-220ms。
- 上下文进度条变化 180ms。
- 开关切换 120ms。
- 不添加大范围弹跳或复杂背景动效。

### 11.9 实施步骤

#### Step A：去除折叠历史重复按钮

文件：

- `frontend/src/components/ai-plan/ConversationSidebar.vue`

动作：

- 删除 `collapsed-restore` 模板节点。
- 删除对应 CSS。
- 保持顶部 collapse button 行为不变。

#### Step B：重构上下文展示

文件：

- `frontend/src/components/ai-plan/ContextPanel.vue`
- `frontend/src/components/ai-plan/ChatInput.vue`
- `frontend/src/views/AIPlanView.vue`

动作：

- `ContextPanel` 改为纯状态指示器。
- `ChatInput` 点击上下文指示器时向上 emit。
- `AIPlanView` 右侧面板补齐上下文详细信息。
- `messages-area` 增加底部保留高度。

#### Step C：新增 Agent Tools 状态管理

文件：

- 新增 `frontend/src/composables/useAgentTools.js`

职责：

- 管理 `webSearchEnabled`。
- 管理当前打开的 drawer 类型。
- 提供 skill/memory 摘要数据接口。

#### Step D：侧栏加入 Agent Tools 分组

文件：

- `frontend/src/components/AppNavigation.vue`
- `frontend/src/components/SvgIcon.vue`

动作：

- 主导航下方新增 `AGENT` 分组。
- 加入 `Skills`、`Memory`、`联网搜索`。
- 点击 `Skills/Memory` 打开右侧抽屉。
- 联网搜索 switch 写入 `useAgentTools`。

#### Step E：新增右侧工具抽屉

文件：

- 新增 `frontend/src/components/AgentToolDrawer.vue`
- 可新增 `frontend/src/components/agent-tools/SkillManagerPanel.vue`
- 可新增 `frontend/src/components/agent-tools/MemoryManagerPanel.vue`

动作：

- 抽屉内提供 Skills / Memory tabs。
- 复用现有 `/api/skills`、`/api/memories`。
- 支持新增、编辑、删除、启停。

#### Step F：AI规划页同步运行设置

文件：

- `frontend/src/views/AIPlanView.vue`
- `frontend/src/components/ai-plan/AgentEventBlock.vue`

动作：

- 右侧 Run Settings 展示联网搜索状态。
- 发送请求时携带 `webSearchEnabled` 字段，若后端暂未使用也不破坏。
- `web_search` 工具事件展示为 `联网搜索`。

### 11.10 验收清单

交互验收：

- 历史栏折叠后只剩顶部 `>` 展开按钮。
- 右侧上下文卡片显示百分比、状态、token 明细、压缩按钮。
- 点击输入区上下文指示器不弹遮挡浮层，而是定位右侧上下文区域。
- 侧栏可打开 Skills 抽屉。
- 侧栏可打开 Memory 抽屉。
- 侧栏可切换联网搜索。
- 抽屉关闭后当前 AI 对话不丢失。

视觉验收：

- AI规划页不出现输入框遮挡回答的情况。
- 右侧面板接近 Gemini `Run settings` 的分组清晰度，但保持 TravelMind 红白风格。
- Agent Tools 分组和主导航有清晰层级，不显得拥挤。
- 移动端不出现横向滚动。

功能验收：

- 原有 AI 规划发送、停止、流式输出不受影响。
- 原有竞技场功能不受影响。
- 原有压缩历史功能仍可触发。
- 原有 skill/memory API 调用路径不变。
- 设置页仍可访问，不因新增抽屉失效。

验证命令：

```powershell
cd frontend
npm run build
```

必要人工检查视口：

- 1440x900
- 1280x720
- 1024x768
- 390x844

### 11.11 风险与处理

风险 1：联网搜索开关后端尚未严格接收。

处理：

- 前端先以非破坏性字段携带。
- 若后端忽略字段，功能不报错。
- 后续再补后端参数接入。

风险 2：`SettingsView.vue` 与新抽屉存在重复逻辑。

处理：

- 第一阶段允许轻量重复，确保改动安全。
- 第二阶段再抽成共享 `SkillManagerPanel` / `MemoryManagerPanel`。

风险 3：右侧面板和抽屉同时出现导致拥挤。

处理：

- 抽屉优先级高于 `Agent Run Panel`。
- 抽屉打开时覆盖右侧区域，不挤压中间对话。

风险 4：历史数据库里的乱码与本轮设计无关。

处理：

- 笔记乱码继续由后端过滤/数据库清理解决。
- 本轮不在前端做二次乱码修复，避免污染 UI 逻辑。

### 11.12 下一步执行建议

优先执行顺序：

1. 删除折叠历史重复按钮。
2. 修复上下文浮层遮挡，改成右侧 Agent Run Panel。
3. 新增 Agent Tools 分组与联网搜索开关。
4. 新增 Skill / Memory 右侧抽屉。
5. 做响应式检查和 `npm run build`。

这套顺序能先解决最明显的视觉错误，再引入 skill、memory、联网搜索的管理能力，降低一次性改动带来的风险。
