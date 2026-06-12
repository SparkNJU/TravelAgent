# TripAgent Skill (技能) 系统架构与设计方案

本方案旨在为 **TripAgent (智能旅游规划助手)** 引入高度灵活且符合 **Anthropic Skills** 范式的“技能”管理引擎。通过本方案，用户可以在前台动态添加、编辑、开启或关闭特定领域的 Travel Skill，并实现 Agent 在运行时按需动态激活与执行。

---

## 一、 Anthropic Skill 核心理念与渐进式披露 (Progressive Disclosure)

根据 Anthropic 的设计规范，技能（Skill）不仅是单纯的“外部工具（Tool）”，更是包含**领域知识、提示词工程（Prompt Engineering）、以及可选的可执行脚本（Scripts）和参考资料（References）**的自包含功能包。

### 1. 结构规范：`SKILL.md`
每一个 Skill 都包含一个 `SKILL.md` 文件，其标准格式如下：
* **YAML Frontmatter (元数据域)**：位于文件顶部，提供 Skill 的核心属性：
  * `name`: 技能唯一标识（如 `budget-optimizer`）
  * `description`: 极其关键。描述**“在什么上下文和触发词下，Agent 应该调用该技能”**。LLM 将据此决定是否激活该技能。
* **Instructions (指令域/提示词)**：技能的主体，告诉 Agent 在激活此技能后需要遵循的具体步骤、工作流和规则。
* **Linked Files (关联资源)**：可选的底层支撑，如可执行 Python 代码（放在 `scripts/` 下）或结构化规则（放在 `references/` 下）。

### 2. 三级渐进式披露机制 (3-Level Loading)
为避免将所有 Skill 提示词一次性塞入 LLM 的 Context Window，造成 Token 浪费和“迷失在上下文中”（Lost in the Middle），我们设计了**三级渐进式披露**：

```mermaid
graph TD
    A[用户输入: "我想对比一下南京和北京的3日游预算，哪个性价比更高？"] --> B{Agent 判断当前启用的所有元数据 Level 1}
    B -- 发现匹配 "compare" 或 "budget" 的 Skill 元数据 --> C[加载 Skill 详细指令 Level 2: SKILL.md Body]
    C --> D{执行过程中是否需要复杂计算或私有知识？}
    D -- 是 --> E[按需执行脚本或读取资料 Level 3: scripts/ 或 references/]
    D -- 否 --> F[LLM 结合 Skill 指令直接生成高质量对比分析]
    F --> G[调用 finish 返回最终规划]
```

1. **Level 1：元数据常驻 (Metadata Loading)**
   * 在 Agent 初始化时，**仅将所有已启用的 Skill 的 `name` 和 `description`** 注入到 System Prompt 的 "Available Skills" 列表中。
2. **Level 2：按需动态激活 (On-Demand Activation)**
   * LLM 判断当前任务适合使用某个 Skill 后，Agent 在 ReAct 的 `THINK` 步骤中决策并触发“加载该 Skill 详细 Instructions”的动作。将 `SKILL.md` 的内容动态插入到当前对话的 System/User Context 中。
3. **Level 3：精准工具调用 (Deterministic Execution)**
   * 如果技能中注册了可执行的 Python 脚本，Agent 可像调用普通 Tool 一样调用这些脚本以获取确定性的计算结果（例如预算优化算法）。

---

## 二、 旅游规划领域下的亮点 Skill 场景设计

为了体现“亮点扩展”和“领域特化”，我们可以为 TripAgent 设计以下内置 Skill，用户也可以在前端自行创建同类 Skill：

| 技能标识 (Name) | 触发场景 (Triggers & Description) | 核心指令 (Instructions) | 可选配套脚本/资料 (Level 3) |
| :--- | :--- | :--- | :--- |
| **`budget-optimizer`**<br>(预算精算与优化专家) | 当用户提到“省钱”、“预算有限”、“高性价比”或“超支”时触发。 | 1. 自动执行“3:2:3:2”消费分配法则（住、食、行、娱）；<br>2. 识别并替换高成本景点为免费/低成本平替。 | `scripts/budget_calculator.py`：计算不同档次的开销上限，并校验整体预算。 |
| **`itinerary-comparator`**<br>(多方案深度对比) | 当用户要求“对比两个城市”、“对比方案A和B”时触发。 | 1. 生成标准的 Markdown 对比矩阵（包含天数、节奏、适宜人群、核心体验）；<br>2. 从交通便利度、预算消耗、视觉震撼度三维度进行打分。 | `references/cities_comparison_matrix.json`：常见城市对标的维度基础数据。 |
| **`packing-helper`**<br>(智能出行行李箱) | 当用户问“带什么衣服”、“需要准备什么”、“行李清单”时触发。 | 根据目的地天气预报、出行天数、伴行人员类型（如亲子/情侣），生成极其详尽的结构化分类清单（衣物、数码、药品、证件）。 | 无（纯 Prompt + 天气 API 数据驱动） |
| **`visa-policy-expert`**<br>(境外游签证申报顾问) | 当目的地为非中国大陆城市，且用户询问“签证”、“护照”、“出境政策”时触发。 | 自动匹配签证政策，提供“免签/落地签/电子签”的申请材料清单、办理时限与避坑贴士。 | `references/visa_rules/`：存储常见国家签证政策的 Markdown 离线包。 |

---

## 三、 系统架构与模块设计

为了在原有的 `agent` (Python), `backend` (Java), `frontend` (Vue) 架构中平滑引入这一功能，我们需要进行如下模块改造：

### 1. 数据库设计 (MySQL)
在 `backend` 的 MySQL 数据库中新增 `agent_skills` 表：

```sql
CREATE TABLE `agent_skills` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL UNIQUE COMMENT '技能唯一英文标识',
    `title` VARCHAR(255) NOT NULL COMMENT '技能中文名称',
    `description` TEXT NOT NULL COMMENT '技能触发描述 (元数据，用于 LLM 决策)',
    `instructions` TEXT NOT NULL COMMENT 'SKILL.md 主体指令内容',
    `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `scripts_code` TEXT COMMENT '可选：挂载的 Python 脚本代码',
    `references_data` TEXT COMMENT '可选：挂载的参考数据 (JSON/JSONL 格式)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. 后端 API 接口设计 (Java / Spring Boot)
后端主要负责 Skill 的 CRUD 持久化，并向 Python Agent 提供已启用 Skill 的元数据与详情接口：
* `GET /api/skills`：获取技能列表（支持按 `is_enabled` 过滤）。
* `POST /api/skills`：新增技能。
* `PUT /api/skills/{id}`：修改技能配置（可开启/关闭技能，或修改 `SKILL.md` 指令）。
* `DELETE /api/skills/{id}`：删除自定义技能。

### 3. Agent 引擎动态改造 (Python / FastAPI)
这是 Skill 发挥作用的核心。我们在原有的 `ReActAgent` 和 `ToolRegistry` 基础上引入 **`SkillEngine`**。

#### A. Level 1 常驻：动态生成 System Prompt
在 `ReActAgent.run` 中，从后端拉取所有 **`is_enabled=True`** 的 Skill 元数据，并将其动态写入 System Prompt：

```python
# 动态拼接进 System Prompt
system_prompt_skills = ""
for skill in enabled_skills:
    system_prompt_skills += f"- Skill Name: {skill['name']}\n  Description: {skill['description']}\n"
```

并在系统提示词中增加如下指令：
> *“You have access to specialized Skills. If a user request falls into the domain of a Skill, you MUST first call the `activate_skill(skill_name)` tool to retrieve its detailed instructions and follow them carefully.”*

#### B. Level 2 激活：新增内置工具 `activate_skill`
我们在 `ToolRegistry` 中实现一个通用的 `ActivateSkillTool`：
* **名称**：`activate_skill`
* **参数**：`skill_name`
* **执行逻辑**：
  1. 从后端/本地加载对应 `skill_name` 的 `instructions`；
  2. 将该 `instructions` 作为一条 `system` 或 `user` 级别的提示词追加到当前的 `messages` 队列中；
  3. 返回：“Skill {skill_name} successfully activated. Detailed instructions loaded into context.”
* **效果**：LLM 收到激活成功的 Observation 后，在后续的 `THINK` 中便会根据新加载的详细指令来指导下一步行动。

#### C. Level 3 联动：动态挂载 Python 执行环境 (沙箱)
如果 Skill 带有 `scripts_code`，在 `activate_skill` 时，`ToolRegistry` 可以将该脚本动态编译为一个新的 `Tool` 实例注册到当前的可用工具列表中，允许 LLM 在需要时调用！

---

## 四、 交互与前端 UI 界面设计

为了给用户呈现极具**科技感与产品化**的体验，我们建议在前端增加一个“Agent 技能工坊 (Skill Studio)”看板：

1. **技能市场/技能列表**：
   * 卡片流布局展现所有内置与自定义技能。
   * 提供精致的 HSL 渐变毛玻璃卡片设计，搭配动态 Toggle 开关，一键启用/关闭技能。
2. **可视化技能流转 (SSE 思考过程展示)**：
   * 在 Agent 运行时，前端 SSE 能够显示 `[激活技能: budget-optimizer] - 正在导入预算分配精算引擎`。
   * 在思考路径图谱中，高亮显示当前处于哪个 Skill 的作用域下，让用户能直观感受到 Agent 正在调用自己配置的技能。
3. **技能编辑器 (Skill Editor)**：
   * 内置 Markdown 编辑器，支持即时预览 `SKILL.md`。
   * 提供 YAML 元数据的可视化表单输入（名称、描述），自动拼接为标准 Anthropics 格式。
   * 提供“技能在线测试”沙箱，用户输入一句话，一键测试该技能是否能被准确触发激活。

---

## 五、 下一步协作分工

既然您负责 **Agent Skill 这一块**，我非常期待与您的紧密配合！我们可以这样分工开展：

1. **您可以主导**：
   * 细化 `SKILL.md` 的 Prompts 编写和旅游场景下的自定义技能定义（如您特别关心的文献对比、预算精算等）。
   * 编写 Level 3 中可选的 Python 算法脚本（例如 `budget_calculator.py` 或特定的解析算法）。
2. **我可以协助您完成**：
   * **数据库与 Java 后端** 的技能 CRUD 接口编写；
   * **Python Agent 端** `activate_skill` 工具的注册逻辑、三级渐进式披露引擎的实现；
   * **Vue 前端** 极具视觉冲击力的“技能管理面板”与 ReAct 思考流中 Skill 激活的可视化展示。
