# 旅行规划微调与高德 MCP 整合改造方案

本方案旨在规范旅行规划系统中「会话微调」、「高德 MCP 工具箱集成」以及「行程的数据库结构化存储」的设计，取代原有《高德MCP接入改造说明》及《会话内计划微调改造计划》，并与《规划工作台界面设计》保持交互对齐。

---

## 一、核心设计思想

1. **单一数据源 (Single Source of Truth) 原则**：
   - 彻底废除原有的 `travel_plans.itinerary` (LONGTEXT) 字段，避免因手动修改导致文本与结构化数据不同步的问题。
   - **以 `plan_activities` 为唯一日程真相**：行程的日程安排全部由 `plan_activities` 结构化子表承载。
   - **动态渲染**：无论是对话页面的详情卡片、个人中心的详情预览，还是可视化工作台，全部通过读取 `plan_activities` 数据列表，由前端采用 Timeline、卡片等高级 UI 组件统一动态渲染。
2. **废除冗余表，保持简洁**：
   - 不新增 `chat_conversations` 表，直接在前端维护会话历史或在 `ai_planning_history` 中记录。
   - 不新增 `geocode_cache` 缓存表，由高德 MCP 与本地模糊词典实时配合进行坐标解析。
3. **“先跳转、后解析”的高级 UX 路由设计**：
   - **对话页快速响应**：当 ReAct Agent 完成规划（触发 `finish` 工具）后，对话页的 AI 回答气泡下方立即渲染显眼的 **「进入可视化工作台 ➜」** 按钮。
   - **跳转时带参**：用户点击按钮后，前端不进行任何等待，立即执行 `router.push('/plan-workbench?c=' + conversationId)` 跳转到工作台页面。
   - **工作台沉浸式解析加载**：
     * 工作台页面在 `onMounted` 检测到 `c`（会话 ID）参数时，首先展示高档的骨架屏与粒子加载动画：**「✨ AI 正在为您初始化地图标点并生成日程轴...」**。
     * 在加载期间，工作台向后端发送 `POST /api/travel/plan/parse-and-save` 请求。
     * 后端在大模型二次提炼并补全高德经纬度坐标后，一次性将数据存入 `travel_plans` 和 `plan_activities`，并向前端返回生成好的 `planId` 和 activities 数组。
     * 工作台拿到数据后，平滑替换路由参数为 `?planId={planId}`，关闭加载动画，展示可交互的时间轴和地图标点。

---

## 二、数据库结构改造

### 2.1 修改 `travel_plans` 表
从 `travel_plans` 表中删除 `itinerary` 字段。

```sql
ALTER TABLE travel_plans DROP COLUMN itinerary;
```

### 2.2 新建 `plan_activities` 表
用于结构化存储每天的每一项活动，提供地图标点与工作台编辑所需字段：

```sql
CREATE TABLE IF NOT EXISTS plan_activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    day_number INT NOT NULL,              -- 第几天（从 1 开始）
    activity_time VARCHAR(50),            -- 活动时间（例如 "09:00 - 10:30" 或 "上午"）
    location_name VARCHAR(255) NOT NULL,  -- 地点/景点名称
    latitude DECIMAL(10, 8),             -- 纬度
    longitude DECIMAL(11, 8),            -- 经度
    description TEXT,                    -- 活动描述/具体条目说明
    tips TEXT,                           -- 贴士/备注
    cost DECIMAL(10, 2) DEFAULT 0.00,    -- 预计花费
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    INDEX idx_plan_id (plan_id),
    INDEX idx_day_number (day_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 三、Java 后端实体类与 API 改造

### 3.1 实体类定义

1. **`PlanActivity.java`**
   ```java
   @Entity
   @Table(name = "plan_activities")
   @Data
   public class PlanActivity {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @Column(name = "plan_id", nullable = false)
       private Long planId;

       @Column(name = "day_number", nullable = false)
       private Integer dayNumber;

       @Column(name = "activity_time")
       private String activityTime;

       @Column(name = "location_name", nullable = false)
       private String locationName;

       private BigDecimal latitude;
       private BigDecimal longitude;

       @Column(columnDefinition = "TEXT")
       private String description;

       @Column(columnDefinition = "TEXT")
       private String tips;

       private BigDecimal cost;
   }
   ```

2. **`TravelPlan.java`**
   ```java
   // 移除原 itinerary 属性，增加一对多关联
   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
   @JoinColumn(name = "plan_id")
   private List<PlanActivity> activities = new ArrayList<>();
   ```

### 3.2 控制器与服务类逻辑
- **解析并持久化（`POST /api/travel/plan/parse-and-save`）**：
  接收 `conversationId`（或消息文本）。先在后端请求 FastAPI 的结构化提取及高德坐标补全，然后将得到的结构化列表存入 `travel_plans` 和 `plan_activities` 中，最后向前端返回 `planId` 和活动列表。
- **更新行程（`PUT /api/travel/plan/{planId}`）**：
  前端在工作台修改时自动触发。后端通过 JPA Orphan Removal 机制级联清空 `plan_activities` 中的旧记录并写入最新修改后的活动数组。
- **查询详情（`GET /api/travel/plan/{planId}`）**：
  级联查询 `plan_activities` 并按 `day_number` 及活动顺序排序，同 `travel_plans` 元数据一并返回给前端。

---

## 四、Agent 侧规范化输出与结构化提取

### 4.1 规范化 Agent 输出模板
在 `agent/services/react_agent.py` 的 System Prompt 中加入 Markdown 格式规范约束，使 Agent 生成的内容高度统一。例如：

```markdown
请按照以下规范格式输出每日行程：
### 第[X]天：[今日主题]
- **[时间点/时间段]** 【[地点/景点名称]】[活动具体描述] | 💡 贴士: [具体贴士，没有可写无] | 💰 预算: [预计花费，仅数字，无则写 0]
```

### 4.2 AI 结构化解析（`itinerary_builder.py`）
当 Agent 最终调用 `finish` 时，我们将使用一个轻量且低 Temperature 的大模型，通过 Strict JSON Schema 或 System Instruction 对 Markdown 内容进行精准提炼，输入为 **Markdown 文本**，输出为标准 JSON：

```json
[
  {
    "day": 1,
    "summary": "今日主题",
    "activities": [
      {
        "time": "时间点",
        "location": "景点名称",
        "description": "活动具体描述",
        "tips": "具体贴士",
        "cost": 150.00
      }
    ]
  }
]
```

### 4.3 高德 MCP 坐标补全与 uuid 注入
1. **本地匹配**：
   - 提取到结构化列表后，对每个 activity，首先使用本地 `LOCAL_LANDMARKS`（常用景点及城市坐标库）进行模糊匹配。
2. **高德 MCP 编码**：
   - 本地未命中，则将该活动地点的地址（加上目的地城市作为前缀以确保精准度，如 "南京 夫子庙"）传递给高德 MCP 接口的 `maps_geo`。
   - 解析出坐标后，更新入 activity 的 `latitude` 和 `longitude`。
3. **前端渲染适配**：
   - 在 FastAPI 返回前，使用 Python 生成唯一的 `uuid`（如 `uuid.uuid4().hex`）回填到 activity 的 `id` 中，便于前端渲染 Marker 与组件排序。

---

## 五、工作台自动同步闭环

- **工作台内编辑**：用户在 Timeline 增删、拖拽排序或地图修改点，页面会立即更新本地 `activities` 状态。
- **静默后台自动同步**：每次修改均自动触发 `PUT /api/travel/plan/{planId}`，实现无感云端同步。

---

## 六、工作台页面路由与跳转触发设计

### 6.1 路由注册与页面创建
1. **新建前端页面**：创建 `frontend/src/views/PlanWorkbenchView.vue` 作为可视化编辑工作台。
2. **注册路由**：在 `frontend/src/router/index.js` 中增加路由项：
   ```javascript
   { 
     path: '/plan-workbench', 
     name: 'planWorkbench', 
     component: () => import('../views/PlanWorkbenchView.vue') 
   }
   ```
3. **入参设计**：
   - 从对话页初次进入：`/plan-workbench?c={conversationId}`
   - 以后再次查看/编辑：`/plan-workbench?planId={planId}`

### 6.2 跳转与加载交互逻辑

1. **跳转触发判定**：
   - 对话流完成时，若 AI 的最后一条消息中包含由 `finish` 工具返回的 `status: "finished"`，则在 AI 回答正文下方渲染亮起 **「进入可视化工作台 ➜」** 按钮。
2. **零延迟跳转**：
   - 用户点击按钮，前端立刻跳转：`router.push('/plan-workbench?c=' + activeConversationId)`。
3. **工作台 Loading 初始化**：
   - 工作台在 `onMounted` 检测到 `c` 参数，启动全屏骨架屏加载动画，向后端发送请求进行解析与入库。
   - 解析完成后，更新 URL 查询参数为 `?planId={planId}` 并展示交互界面。
4. **回跳对话页**：
   - 工作台顶栏保留「回对话」按钮，点击时带上 `c` 或 `planId` 回跳对话页。

---

## 七、实施任务分解 (Task List)

1. **Task 1: 数据库与实体类结构化改造**
   - [ ] 修改 `schema.sql`，从 `travel_plans` 表中移除 `itinerary` 字段，增加 `plan_activities` 结构化子表。
   - [ ] 在 Java 后端创建 `PlanActivity.java` 实体类并在 `TravelPlan.java` 中配置级联 `@OneToMany(orphanRemoval = true)` 关系。
   - [ ] 实现 `PlanActivityRepository` 并修改 `TravelPlanService`/`TravelController` 的 CRUD 接口，支持级联 activities 保存与读取。
2. **Task 2: AMap MCP 客户端集成**
   - [ ] 编写 `amap_mcp_client.py` 建立与官方网关的 SSE/HTTP 通讯。
   - [ ] 编写 `mcp_proxy_tool.py` 动态拉取高德 MCP 工具并注册到 `ToolRegistry`。
3. **Task 3: 提取器与坐标补全开发**
   - [ ] 编写 `itinerary_builder.py`，配置轻量级大模型二次提取器，实现 Markdown -> JSON 数组的转换。
   - [ ] 整合 `FinishTool`，使之能自动调用高德 `maps_geo` 获取经纬度并补全结构化对象，回填 uuid 供前端作为 key。
4. **Task 4: 前端路由注册、工作台占位与自动保存/跳转集成**
   - [ ] 在 `router/index.js` 注册 `/plan-workbench` 路由，并创建工作台空白页面 `PlanWorkbenchView.vue`。
   - [ ] 在 `AIPlanView.vue` 中，当流式返回 `done` 事件且触发 `finish` 时，在聊天气泡下方渲染 `「进入可视化工作台」` 按钮。
   - [ ] 点击按钮，直接 `router.push('/plan-workbench?c=' + conversationId)`。
5. **Task 5: 前端工作台界面适配与自动同步联调**
   - [ ] 实现 `PlanWorkbenchView.vue` 的 `c` 参数加载 loading 动画，调用 `/api/travel/plan/parse-and-save` 后台持久化并转换 URL。
   - [ ] 联调工作台组件加载，从 `plan_activities` 渲染 Timeline 与高德地图。
   - [ ] 适配 Timeline 手动修改自动触发 `PUT /api/travel/plan/{planId}` 后台静默同步。
