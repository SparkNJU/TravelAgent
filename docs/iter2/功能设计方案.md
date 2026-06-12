# 添加规划功能方案

## 一、数据结构设计

根据 AI 生成的南京攻略样例，规划的核心数据分为三层：

### 1. 规划基本信息 — 存在现有 `travel_plans` 表（调整字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 所属用户 |
| title | VARCHAR(255) | 标题，如"南京3-4天文化历史深度游" |
| destination_name | VARCHAR(100) | 目的地 |
| days | INT | 天数 |
| estimated_budget | VARCHAR(100) | 改为文本，如"舒适型（人均5000-15000元）" |
| interests | VARCHAR(255) | 偏好标签 |
| travel_style | VARCHAR(50) | 旅行风格 |
| status | VARCHAR(20) | draft/saved |
| created_at / updated_at | TIMESTAMP | 时间戳 |

### 2. 每日行程 — 新增 `plan_days` 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| plan_id | BIGINT | FK → travel_plans |
| day_number | INT | 第几天 |
| morning | TEXT | 上午安排 |
| afternoon | TEXT | 下午安排 |
| evening | TEXT | 晚上安排 |
| food | TEXT | 当日美食推荐 |
| accommodation | TEXT | 当日住宿建议 |
| notes | TEXT | 其他备注 |

### 3. 附加信息 — 在 `travel_plans` 表加字段

| 字段 | 类型 | 说明 |
|------|------|------|
| weather_tips | TEXT | 天气提示 |
| accommodation_tips | TEXT | 住宿总建议 |
| practical_tips | TEXT | 实用贴士 |

---

## 二、前端交互流程

### 手动创建规划（从零填表单）

1. 用户在"我的规划"或 AI 规划页面点击"新建规划"
2. 弹出表单，填写：标题、目的地、天数、预算、旅行风格、兴趣偏好
3. 点击"创建"→ 调用 `POST /api/travel/plan/save`，每日行程为空
4. 创建成功后进入编辑视图，可以逐天填写上午/下午/晚上/美食/住宿
5. 也可以跳转到 AI 规划页面，让 AI 补充内容

### AI 规划保存

1. AI 回复完成后，用户点击"保存规划"
2. 后端或 Agent 解析 AI 回复，提取结构化数据（标题、目的地、每日行程等）
3. 调用同一个 `POST /api/travel/plan/save` 接口，传入结构化数据
4. 保存后可以在"我的规划"中查看和逐字段编辑

### 查看和编辑规划

1. "我的规划"列表展示所有已保存规划（标题、目的地、天数、日期）
2. 点击"查看"打开详情面板，按天展示行程，渲染 Markdown
3. 点击"编辑"切换为编辑模式，每个字段变为可编辑的输入框
4. 修改后"保存"调用 `PUT /api/travel/plan/{id}` 更新

---

## 三、后端改动

- `TravelPlan` 实体：新增 `weatherTips`、`accommodationTips`、`practicalTips` 字段
- 新增 `PlanDay` 实体 + `PlanDayRepository`
- `SavePlanRequest`：改为接收结构化数据（含每日行程列表）
- `TravelPlanResponse`：返回结构化数据（含每日行程列表），添加 `createdAt`
- `TravelPlanService`：save/update 逻辑调整，支持每日行程的增删改
- `PUT /api/travel/plan/{id}`：支持更新基本字段和每日行程

---

## 四、前端改动

- **ProfileView.vue**：新建规划按钮 + 创建表单弹窗 + 详情展示改为结构化 + 编辑模式
- **AIPlanView.vue**：保存时发送结构化数据而非原始 Markdown
- 新增 `PlanDayForm` 组件（可选，也可内联）：每日行程编辑表单

---

## 五、争议点（待讨论确认）

### 争议 1：AI 回复的格式化内容由谁负责？

- **方案 A — Agent 端（Python）输出结构化 JSON**
  - Agent 在生成回复时，除了流式输出 Markdown 给前端展示，同时返回一份结构化 JSON（包含每日行程、美食、贴士等）
  - 优点：Agent 最了解自己输出的内容，提取准确
  - 缺点：需要改 Agent 端代码，增加一个结构化输出步骤

- **方案 B — 后端（Java）从 Markdown 中解析提取**
  - 前端保存时发原始 Markdown 到后端，后端用正则/规则解析
  - 优点：不改 Agent 端
  - 缺点：AI 回复格式不完全固定，解析不稳定

- **方案 C — 前端在保存时解析**
  - 前端 JS 从 Markdown 中提取结构化数据，直接发送结构化请求
  - 优点：后端简单
  - 缺点：前端逻辑重，解析不稳定

### 争议 2：触发保存的时机

- 当前方案：AI 回复完成后用户手动点击"保存"
- 是否需要自动保存草稿（AI 回复完成即存为 draft）？

### 争议 3：手动创建是否需要 AI 辅助？

- 你说手动创建是从零填表单，不引入 AI
- 那手动创建就只填基本信息 + 空白每日行程，后续用户自己填写
- 还是说手动创建也提供一个"让 AI 帮我填充"的选项？

### 争议 4：每日行程的字段粒度

- 当前设计：morning / afternoon / evening / food / accommodation / notes
- 是否需要更细？比如每个时段支持多个地点/活动（数组）
- 还是当前粒度够用，用户需要的话写在 notes 里

### 争议 5：estimated_budget 改为 VARCHAR 是否合适

- 当前是 DECIMAL，但 AI 输出是"舒适型（人均5000-15000元）"这样的文本
- 改为 VARCHAR 可以存文字描述，但失去了数值计算能力
- 是否拆成两个字段：`budget_min` / `budget_max` (DECIMAL) + `budget_desc` (VARCHAR)？