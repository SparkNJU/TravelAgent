# TravelMind Agent 设计详细文档

## 一、概述

### 1.1 模块定位

Agent 模块是 TravelMind 旅行规划平台的智能核心，负责接收用户的自然语言旅行需求，通过大语言模型（LLM）进行推理规划，自主调用外部工具获取实时信息，最终生成个性化的旅行方案。

Agent 模块采用 **FastAPI** 构建，独立部署为一个微服务（默认端口 8000），通过 HTTP/SSE 协议与 Backend 进行通信，与前后端完全解耦。

### 1.2 核心能力

| 能力 | 说明 |
|------|------|
| **自主规划** | Agent 不是简单的预定义 workflow，而是基于 LLM 进行自主思考和决策，动态决定下一步行动 |
| **工具调用** | 具备联网搜索、文件解析、用户交互等外部工具调用能力 |
| **自我修正** | 能够捕获工具调用异常，通过 LLM 自动生成修正参数进行重试 |
| **反思改进** | 基于 Reflexion 框架，对生成的方案进行自我评估，发现问题后自动修正 |
| **状态可视化** | 通过 SSE 流式事件，将思考过程、工具调用、观察结果等内部状态实时推送给前端 |

### 1.3 技术栈

| 组件 | 技术 |
|------|------|
| Web 框架 | FastAPI + Uvicorn |
| LLM 通信 | OpenAI SDK（兼容模式，对接阿里云 DashScope） |
| 数据校验 | Pydantic |
| 配置管理 | YAML + dotenv + 环境变量 |
| 流式输出 | Server-Sent Events (SSE) |

---

## 二、整体架构

### 2.1 系统架构总览

Agent 模块在整个 TravelMind 平台中的位置如下：

```
┌─────────────────────────────────────────────────────┐
│  Frontend (Vue 3 + Vite)                           │
│  - 对话 UI、流式 Markdown 渲染、文件上传             │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP (via Vite proxy)
┌──────────────────────▼──────────────────────────────┐
│  Backend (Spring Boot)                              │
│  - 用户认证、会话管理、消息持久化                     │
│  - 桥接 Frontend 与 Agent 的请求转发                 │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP/SSE
┌──────────────────────▼──────────────────────────────┐
│  Agent (FastAPI)  ◀── 本文档核心                     │
│  ┌────────────┐  ┌────────────┐  ┌──────────────┐  │
│  │ MetaPlanner │  │ ReActAgent │  │ Reflection   │  │
│  │ (元规划器)   │→│ (推理引擎)  │→│ Agent(反思)  │  │
│  └────────────┘  └─────┬──────┘  └──────────────┘  │
│                        │                            │
│  ┌─────────────────────▼──────────────────────────┐ │
│  │              Tool Registry (工具注册中心)        │ │
│  │  WebSearch │ FileParser │ AskUser │ Finish ... │ │
│  └────────────────────────────────────────────────┘ │
│                        │                            │
│  ┌─────────────────────▼──────────────────────────┐ │
│  │           LLM Service (大模型通信层)            │ │
│  │     OpenAI SDK ↔ DashScope / 兼容 API           │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 2.2 模块目录结构

```
agent/
├── main.py                    # FastAPI 入口，路由定义与服务初始化
├── models.py                  # 请求/响应 Pydantic 模型
├── config/
│   ├── __init__.py            # 配置加载器（YAML + .env + 环境变量）
│   └── default.yaml           # 默认配置文件
├── services/
│   ├── llm_service.py         # LLM 通信服务（封装 OpenAI SDK）
│   ├── planner.py             # MetaPlanner 元规划器
│   ├── react_agent.py         # ReAct Agent 核心推理引擎
│   ├── reflection_agent.py    # Reflection Agent 反思改进模块
│   ├── tool_registry.py       # 工具抽象接口、注册中心与内置工具
│   ├── serper_client.py       # Serper 搜索引擎客户端
│   ├── file_parser.py         # 文件解析工具（txt/pdf/docx）
│   └── sse_events.py          # SSE 事件格式化辅助函数
├── docs/                      # 文档目录
├── requirements.txt           # Python 依赖
├── .env                       # 环境变量（敏感 Key）
└── .env.example               # 环境变量模板
```

---

## 三、核心组件设计

### 3.1 LLM Service（大模型通信层）

**文件**: `services/llm_service.py`

LLM Service 是所有与大语言模型交互的统一入口，封装了 OpenAI SDK 的调用细节，提供四种通信模式。

#### 类设计

```python
class LLMService:
    _client: OpenAI       # OpenAI 兼容客户端
    _model: str           # 模型名称（如 qwen3.6-plus）
    _temperature: float   # 生成温度
    _max_tokens: int      # 最大 token 数
```

#### 四种通信模式

| 方法 | 用途 | 返回值 | 调用者 |
|------|------|--------|--------|
| `chat(messages, temperature)` | 普通单轮对话 | `str` | ReflectionAgent 评估、SuggestQuestionsTool |
| `chat_stream(messages, temperature)` | 流式对话 | `Generator[str]` | MetaPlanner 生成计划 |
| `chat_json(messages, json_schema)` | 结构化 JSON 输出 | `dict` | ReflectionAgent 评估打分 |
| `chat_with_tools(messages, tools, temperature)` | 工具调用对话 | `Message` | ReActAgent 推理循环 |

#### 设计要点

- 使用 `openai.OpenAI` 客户端，通过 `base_url` 对接不同 LLM 提供商（默认阿里云 DashScope）
- API Key 通过环境变量注入，不在代码中硬编码
- `chat_with_tools` 使用 OpenAI 的 Function Calling 协议，`tool_choice="auto"` 让模型自主决定是否调用工具
- `chat_json` 使用 `response_format` 中的 `json_schema` 约束输出格式，确保结构化评估结果

---

### 3.2 MetaPlanner（元规划器）

**文件**: `services/planner.py`

MetaPlanner 是 Plan-and-Execute 推理框架中的 "Plan" 阶段实现。它接收用户的旅行需求，调用 LLM 生成一份结构化的执行计划，供 ReAct Agent 后续遵循执行。

#### 工作原理

```
用户需求 → LLM 生成执行计划 → ReAct Agent 按计划逐步执行
```

#### 核心方法

```python
class MetaPlanner:
    def generate_plan(self, query: str, file_summary: str = "") -> Generator[str, None, None]:
        # 构建 system prompt + user message
        # 流式调用 LLM 生成计划
        # 逐 chunk 以 SSE 事件（type: "plan"）推送
```

#### System Prompt 设计

MetaPlanner 的 System Prompt 指导 LLM 生成的计划需包含：
1. 每一步要做什么
2. 使用哪个工具（`web_search`、`parse_file` 或 `reasoning`）
3. 该步骤预期获取什么信息

输出格式为编号列表，控制在 5-8 步，确保计划具体且可执行。

#### 流式输出

MetaPlanner 通过 `LLMService.chat_stream()` 流式生成计划内容，每个 chunk 包装为 `{"type": "plan", "content": "..."}` 的 SSE 事件，前端可实时展示计划生成过程。

---

### 3.3 ReAct Agent（核心推理引擎）

**文件**: `services/react_agent.py`

ReAct Agent 是整个系统的核心，实现了经典的 **ReAct（Reasoning + Acting）** 推理框架。它以 Think-Act-Observe 循环驱动，自主决定何时调用工具、如何处理结果、何时给出最终答案。

#### 类设计

```python
class ReActAgent:
    _llm: LLMService              # 大模型服务
    _tools: ToolRegistry           # 工具注册中心
    _max_iterations: int           # 最大迭代次数（默认 16）
    _max_retries: int              # 工具调用最大重试次数（默认 2）
```

#### ReAct 循环详解

```
┌──────────────────────────────────────────────────────────┐
│                    ReAct 循环                              │
│                                                          │
│   ┌─────────┐    ┌─────────┐    ┌───────────┐           │
│   │  THINK  │───▶│   ACT   │───▶│ OBSERVE   │           │
│   │ 分析现状  │    │ 调用工具  │    │ 处理结果   │           │
│   └─────────┘    └────┬────┘    └─────┬─────┘           │
│        ▲              │               │                  │
│        │              ▼               │                  │
│        │         ┌─────────┐          │                  │
│        │         │  判断    │          │                  │
│        │         │ 是否完成? │          │                  │
│        │         └────┬────┘          │                  │
│        │              │               │                  │
│        │    ┌─────────┴─────────┐     │                  │
│        │    │ Yes: finish工具   │     │                  │
│        │    │ → 输出最终答案     │     │                  │
│        │    │ No:  继续循环      │     │                  │
│        └────┘                    └─────┘                  │
└──────────────────────────────────────────────────────────┘
```

#### 执行流程

**Step 1: 初始化**
- 构建 System Prompt，注入可用工具列表
- 组装 User Message，包含用户查询、文件摘要（如有）、执行计划（如有）、聊天历史

**Step 2: 循环迭代**（最多 `max_iterations` 轮）

每一轮迭代包含三个阶段：

1. **THINK（思考）**: 调用 `LLMService.chat_with_tools()`，LLM 分析当前状态并决定下一步行动。LLM 的思考内容通过 `{"type": "thought"}` 事件推送。

2. **ACT（行动）**: 如果 LLM 返回了 `tool_calls`，解析每个工具调用的函数名和参数，通过 `ToolRegistry.call()` 执行工具。工具调用信息通过 `{"type": "action"}` 事件推送。

3. **OBSERVE（观察）**: 获取工具执行结果，将结果截断至 2000 字符后通过 `{"type": "observation"}` 事件推送，并将完整结果（最多 4000 字符）追加到消息历史中供 LLM 下轮参考。

**Step 3: 终止判断**
- LLM 调用 `finish` 工具 → 提取最终答案，通过 `{"type": "answer"}` 事件推送，循环结束
- LLM 调用 `ask_user` 工具 → 等待用户输入，通过 `{"type": "ask_user"}` 事件推送，循环结束
- 达到最大迭代次数 → 推送 `{"type": "error"}` 事件

#### System Prompt 设计

```
You are a travel planning assistant agent.

You have access to the following tools:
{tools}

Follow the ReAct pattern:
1. THINK: Analyze what information you need next
2. ACT: Call a tool to gather information
3. OBSERVE: Review the tool's result

When you have gathered enough information:
- Output the complete travel plan as your message content
- Call the `finish` tool with the complete travel plan
- You may call `suggest_questions` in the same turn

Important:
- Always search for up-to-date information
- Check weather forecasts for the travel dates
- If a tool call fails, try with different parameters
- Use ask_user to clarify user preferences when essential info is missing
```

---

### 3.4 Reflection Agent（反思改进模块）

**文件**: `services/reflection_agent.py`

Reflection Agent 在 ReAct Agent 的基础上增加了 **Reflexion（自我反思）** 能力，实现了 "生成→评估→改进" 的闭环。

#### 工作流程

```
┌──────────────────────────────────────────────────────────┐
│                 Reflection 循环                           │
│                                                          │
│   ┌─────────────┐     ┌──────────────┐                  │
│   │ ReAct Agent  │────▶│  自我评估     │                  │
│   │ 生成旅行方案  │     │ (LLM 评审)   │                  │
│   └─────────────┘     └──────┬───────┘                  │
│                              │                           │
│                    ┌─────────┴──────────┐                │
│                    │                    │                │
│               satisfactory        needs_improvement      │
│                    │                    │                │
│                    ▼                    ▼                │
│               输出最终方案      生成改进建议               │
│                                  │                       │
│                                  ▼                       │
│                           ReAct Agent                    │
│                           (携带改进指令重新生成)           │
│                                  │                       │
│                            (最多 1 轮修订)                │
└──────────────────────────────────────────────────────────┘
```

#### 评估机制

Reflection Agent 使用结构化 JSON 输出进行质量评估，评估维度包括：

| 维度 | 说明 |
|------|------|
| **完整性** | 是否覆盖了旅行全程的每日细节 |
| **具体性** | 是否包含真实的景点名称、餐厅、交通信息 |
| **准确性** | 信息是否现实且最新 |
| **结构性** | 是否逻辑清晰、每日分类合理 |

评估结果为结构化 JSON：

```json
{
  "verdict": "satisfactory" | "needs_improvement",
  "issues": ["问题1", "问题2"],
  "suggestions": "具体改进建议"
}
```

#### 改进策略

当评估结果为 `needs_improvement` 时：
1. 将发现的问题和改进建议追加到执行计划中
2. 以修改后的计划重新调用 ReAct Agent 生成方案
3. 最多进行 1 轮修订（`max_revisions=1`），超过后使用当前版本

---

### 3.5 Tool Registry（工具注册中心）

**文件**: `services/tool_registry.py`

Tool Registry 采用 **策略模式**，通过抽象基类定义统一的工具接口，支持动态注册和调用。

#### 抽象接口

```python
class Tool(ABC):
    @property
    def name(self) -> str: ...           # 工具名称
    @property
    def description(self) -> str: ...    # 工具描述（供 LLM 理解）
    @property
    def parameters_schema(self) -> dict: ...  # JSON Schema 参数定义
    def execute(self, **kwargs) -> Any: ...   # 执行逻辑
```

#### 注册中心

```python
class ToolRegistry:
    _tools: dict[str, Tool]  # name → Tool 实例

    def register(tool: Tool)       # 注册工具
    def get(name: str) -> Tool     # 获取工具
    def list_tools() -> list[dict] # 列出所有工具（OpenAI Function Calling 格式）
    def call(name, arguments)      # 调用工具
```

`list_tools()` 返回的格式直接兼容 OpenAI 的 Function Calling 协议：

```json
[
  {
    "type": "function",
    "function": {
      "name": "web_search",
      "description": "Search the web for travel information...",
      "parameters": { "type": "object", "properties": {...} }
    }
  }
]
```

---

### 3.6 SSE Event（流式事件系统）

**文件**: `services/sse_events.py`

Agent 通过 Server-Sent Events (SSE) 将内部状态实时推送给客户端。每个事件为一个 JSON 对象：

```json
{
  "type": "event_type",
  "content": "事件内容",
  "metadata": {}
}
```

#### 事件类型定义

| 事件类型 | 触发时机 | content 内容 | metadata |
|----------|----------|-------------|----------|
| `plan` | MetaPlanner 生成计划 | 计划文本 chunk | `{}` |
| `thought` | ReAct Agent 思考阶段 | 思考内容 | `{step: N}` |
| `action` | 调用工具 | `"Calling tool: name(args)"` | `{step, tool}` |
| `observation` | 工具返回结果 | 结果文本（截断至 2000 字符） | `{step, tool}` |
| `answer` | 最终旅行方案 | 完整 Markdown 方案 | `{step}` |
| `ask_user` | 等待用户输入 | 提示信息 | `{questions: [...]}` |
| `suggestions` | 推荐后续问题 | `""` | `{questions: ["问题1", ...]}` |
| `reflection` | 反思评估结果 | 评估摘要 | `{verdict, revision}` |
| `error` | 错误信息 | 错误描述 | `{}` |
| `done` | 流结束信号 | `""` | `{}` |

结束标志：`data: [DONE]`

---

### 3.7 Config（配置管理）

**文件**: `config/__init__.py`、`config/default.yaml`

配置系统采用 **三层覆盖** 策略，优先级从低到高：

```
default.yaml → local.yaml → 环境变量
```

#### 配置结构

```yaml
llm:
  base_url: "https://dashscope.aliyuncs.com/compatible-mode/v1"
  api_key_env: "DASHSCOPE_API_KEY"
  chat_model: "qwen3.6-plus"
  temperature: 0.7
  max_tokens: 4096

agent:
  max_iterations: 16
  self_correction_retries: 2

tools:
  serper:
    enabled: true
    api_key_env: "SERPER_API_KEY"
  file_parser:
    enabled: true
```

#### Pydantic 模型

配置通过 Pydantic `BaseModel` 进行类型校验：

- `LLMConfig`: LLM 连接参数
- `AgentConfig`: Agent 行为参数（迭代次数、重试次数）
- `ToolsConfig`: 工具开关与密钥配置
- `AppConfig`: 顶层聚合配置

#### 环境变量覆盖

支持通过环境变量覆盖任意配置项，例如：
- `LLM_BASE_URL`、`LLM_MODEL`、`LLM_TEMPERATURE`
- `AGENT_MAX_ITERATIONS`、`AGENT_SELF_CORRECTION_RETRIES`
- `TOOLS_SERPER_ENABLED`

模块加载时自动创建单例 `config` 对象供全局使用。

---

## 四、推理框架详解

本系统有机结合了三种推理框架，形成了完整的智能推理链路。

### 4.1 ReAct（Reasoning + Acting）

**核心思想**: 将推理（Reasoning）和行动（Acting）交织进行，每一步先思考再行动再观察。

**在本系统中的实现**:

```
ReActAgent.run()
  │
  ├── for step in range(1, max_iterations + 1):
  │     │
  │     ├── THINK: LLM 分析当前状态 → 生成思考内容 + 可能的工具调用
  │     │
  │     ├── ACT: 解析 tool_calls → ToolRegistry.call(tool_name, args)
  │     │         └── 失败时: _execute_with_retry → _self_correct → 重试
  │     │
  │     └── OBSERVE: 将工具结果追加到消息历史 → 进入下一轮
  │
  └── 终止: finish 工具 → 输出最终答案
```

**特点**:
- LLM 自主决定调用什么工具、何时停止
- 消息历史不断累积，LLM 可以回顾之前的思考和工具结果
- 工具调用失败时自动纠错重试

### 4.2 Plan-and-Execute

**核心思想**: 先生成全局执行计划，再按计划逐步执行。

**在本系统中的实现**:

```
请求 (generate_plan_first=true)
  │
  ├── Phase 1 - Plan:
  │     MetaPlanner.generate_plan(query)
  │       → LLM 流式生成 5-8 步执行计划
  │       → 计划以 SSE plan 事件推送
  │
  └── Phase 2 - Execute:
        ReActAgent.run(query, execution_plan=plan)
          → 将计划注入 System Prompt
          → ReAct 循环按计划逐步执行
```

**触发方式**: 请求参数 `generate_plan_first: true`（默认关闭，需显式开启）

**计划注入**: 执行计划作为用户消息的一部分传入 ReAct Agent，在 System Prompt 中体现为 "Execution plan to follow"，引导 Agent 按计划行动。

### 4.3 Reflexion（自我反思）

**核心思想**: 执行完成后进行自我评估，发现问题则带着改进建议重新执行。

**在本系统中的实现**:

```
ReflectionAgent.run()
  │
  ├── Round 0:
  │     ReActAgent.run(query, plan)
  │       → 生成旅行方案 answer_0
  │
  ├── 评估:
  │     _evaluate(query, answer_0)
  │       → LLM 结构化输出 JSON 评估
  │       → verdict / issues / suggestions
  │
  ├── 判断:
  │     if verdict == "satisfactory":
  │       → 输出 answer_0，结束
  │
  │     if verdict == "needs_improvement":
  │       → 将 issues + suggestions 追加到 plan
  │       → 生成 revised_plan
  │
  └── Round 1 (如有):
        ReActAgent.run(query, revised_plan)
          → 生成改进方案 answer_1
          → 输出 answer_1，结束（最多 1 轮修订）
```

**评估 Prompt 设计**: 要求 LLM 从完整性、具体性、准确性、结构性四个维度评估旅行方案质量。

### 4.4 三种框架的组合策略

```
请求进入
  │
  ├── mode="plan":
  │     仅执行 MetaPlanner，输出计划，不执行 Agent
  │
  ├── mode="agent":
  │     [可选] MetaPlanner 生成计划
  │     → ReActAgent 执行推理
  │
  └── mode="reflection":
        [可选] MetaPlanner 生成计划
        → ReflectionAgent = ReAct + 自我评估 + 修订
```

| 模式 | 推理框架组合 | 适用场景 |
|------|-------------|----------|
| `plan` | 仅 Plan | 只需要计划，不需要执行 |
| `agent` | [Plan] + ReAct | 标准旅行规划 |
| `reflection` | [Plan] + ReAct + Reflexion | 高质量旅行规划，需要自我改进 |

---

## 五、工具系统设计

### 5.1 内置工具一览

| 工具名 | 类名 | 功能 | 参数 |
|--------|------|------|------|
| `web_search` | `WebSearchTool` | 联网搜索旅行信息 | `query`(必填), `num`(默认5) |
| `parse_file` | `FileParserTool` | 解析上传文件文本 | `file_name`(必填), `file_base64`(必填) |
| `ask_user` | `UserConfirmTool` | 向用户提问获取偏好 | `message`(必填), `questions`(必填) |
| `suggest_questions` | `SuggestQuestionsTool` | 生成推荐后续问题 | `context`(必填) |
| `finish` | `FinishTool` | 完成旅行方案输出 | `answer`(必填) |

### 5.2 WebSearchTool（联网搜索）

**实现**: 封装 `SerperClient`，通过 Serper API 调用 Google 搜索。

```python
class WebSearchTool(Tool):
    name = "web_search"
    description = "Search the web for travel information, attractions, food, and transportation tips."
    
    def execute(self, query: str, num: int = 5) -> list[dict]:
        return self._serper.search(query, num=num)
```

**SerperClient** 通过 HTTP POST 调用 `https://google.serper.dev/search`，返回 Google 搜索的有机结果列表。API Key 通过 `SERPER_API_KEY` 环境变量配置。当未配置时，`enabled` 属性返回 `False`，搜索返回空结果。

### 5.3 FileParserTool（文件解析）

**实现**: 解析用户上传的文件（txt/pdf/docx），提取文本内容。

```
Base64 编码文件 → 临时文件 → 按扩展名选择解析器 → 提取文本 → 清理临时文件
```

支持的文件格式：
- `.txt`: 直接解码（UTF-8/GBK/Latin-1 自动检测）
- `.pdf`: 使用 PyPDF2 逐页提取文本
- `.docx`: 使用 python-docx 提取段落文本

### 5.4 UserConfirmTool（用户交互）

**实现**: 当 Agent 需要用户确认偏好、预算、日期等信息时调用。

返回结构化 JSON：

```json
{
  "status": "waiting_for_user",
  "message": "我需要了解您的旅行偏好",
  "questions": [
    {
      "question": "您更喜欢哪种旅行风格？",
      "options": ["文化历史", "自然风光", "美食购物", "冒险刺激"]
    }
  ]
}
```

ReAct Agent 检测到 `ask_user` 工具调用后，通过 `{"type": "ask_user"}` 事件将问题推送给前端，前端展示选项让用户选择后，将答案带回新一轮对话。

### 5.5 SuggestQuestionsTool（推荐问题）

**实现**: 在旅行方案生成完成后，基于对话上下文由 LLM 自动生成 3 个推荐后续问题。

使用正则表达式从 LLM 输出中提取 JSON 数组，容错处理确保即使 LLM 输出格式不规范也能正常工作。

### 5.6 FinishTool（完成输出）

**实现**: Agent 完成旅行方案后调用，携带完整的 Markdown 格式旅行计划。

ReAct Agent 检测到 `finish` 工具调用后，提取 `answer` 字段内容，通过 `{"type": "answer"}` 事件推送，然后终止循环。

### 5.7 自我纠错机制

当工具调用失败时，ReAct Agent 不会直接报错，而是通过 `_execute_with_retry` 和 `_self_correct` 进行自动修复：

```
工具调用
  │
  ├── 成功 → 返回结果
  │
  └── 失败 → _self_correct(tool_name, bad_args, error_msg)
              │
              ├── LLM 分析错误原因 → 生成修正后的参数
              │
              └── 用修正参数重试 → 最多重试 max_retries 次
                  │
                  ├── 成功 → 返回结果
                  └── 仍失败 → 返回错误信息
```

Self-correction Prompt 示例：

```
The tool 'web_search' was called with arguments {"query": "北京景点"}
but failed with error: Connection timeout.
Please provide corrected arguments as JSON.
```

---

## 六、数据流与通信

### 6.1 请求处理全流程

```
Frontend POST /api/assistant/chat
  │
  ▼
Backend TripAssistantService
  │  转换为 JSON，转发到 Agent
  ▼
Agent POST /api/agent/chat
  │
  ├── 解析 AgentChatRequest
  │     query, user_id, mode, generate_plan_first,
  │     model, temperature, file_name, file_base64, chat_history
  │
  ├── [可选] 解析上传文件
  │     parse_uploaded_file(file_name, file_base64) → file_summary
  │
  ├── [可选] 创建自定义 LLM 实例
  │     如果 request.model 或 request.temperature 有值
  │
  ├── 根据 mode 分发处理:
  │     "plan"        → MetaPlanner.generate_plan()
  │     "agent"       → [MetaPlanner] → ReActAgent.run()
  │     "reflection"  → [MetaPlanner] → ReflectionAgent.run()
  │
  ├── [可选] 生成推荐问题
  │     SuggestQuestionsTool.execute()
  │
  └── 返回 StreamingResponse (SSE)
```

### 6.2 Backend 与 Agent 的交互协议

**请求格式**（Backend → Agent）:

```json
{
  "query": "帮我做一份南京的一日游攻略",
  "user_id": 1,
  "mode": "agent",
  "generate_plan_first": true,
  "model": null,
  "temperature": null,
  "file_name": null,
  "file_base64": null,
  "chat_history": []
}
```

**响应格式**（Agent → Backend → Frontend）:

```
data: {"type": "plan", "content": "第1步：搜索南京热门景点...", "metadata": {}}

data: {"type": "thought", "content": "Step 1: 用户想要南京一日游，我需要先搜索...", "metadata": {"step": 1}}

data: {"type": "action", "content": "Calling tool: web_search({\"query\": \"南京一日游必去景点 2024\"})", "metadata": {"step": 1, "tool": "web_search"}}

data: {"type": "observation", "content": "[{\"title\": \"中山陵\", ...}]", "metadata": {"step": 1, "tool": "web_search"}}

data: {"type": "answer", "content": "# 南京一日游攻略\n\n## 上午\n...", "metadata": {"step": 5}}

data: {"type": "suggestions", "content": "", "metadata": {"questions": ["南京有什么特色小吃？", "中山陵需要预约吗？", "南京博物院怎么去？"]}}

data: [DONE]
```

### 6.3 聊天历史支持

Agent 支持通过 `chat_history` 字段传入历史对话记录，实现多轮对话上下文延续：

```json
{
  "chat_history": [
    {"role": "user", "content": "我想去北京玩3天"},
    {"role": "assistant", "content": "好的，我为您规划了北京3日游..."},
    {"role": "user", "content": "故宫的门票怎么买？"}
  ]
}
```

历史消息会被追加到 ReAct Agent 的消息列表中，使 LLM 能够理解对话上下文。

---

## 七、错误处理与容错

### 7.1 分层容错策略

| 层级 | 异常场景 | 处理方式 |
|------|----------|----------|
| **工具层** | 网络超时、API 限流、解析失败 | `_execute_with_retry` 自动重试 + Self-correction |
| **Agent 层** | 工具调用持续失败 | 返回错误信息，不阻断流程 |
| **推理层** | 达到最大迭代次数 | 推送 `error` 事件，终止循环 |
| **LLM 层** | LLM 调用异常 | 返回错误事件 + `[DONE]` |
| **反思层** | 评估失败 | 降级为 "satisfactory"，直接输出当前方案 |
| **全局层** | 未预期异常 | 捕获并推送 `error` 事件 + `[DONE]` |

### 7.2 Self-correction 流程

```python
def _execute_with_retry(self, tool_name, arguments):
    for attempt in range(max_retries + 1):
        try:
            return tool_registry.call(tool_name, arguments)
        except Exception as e:
            if attempt < max_retries:
                fixed = _self_correct(tool_name, arguments, str(e))
                if fixed:
                    arguments = fixed  # 用修正后的参数重试
    return {"error": "Tool failed after N attempts"}
```

### 7.3 降级策略

- **Agent 不可用时**: Backend 返回本地 fallback 旅行草案，前端仍可展示基础内容
- **Serper 未配置时**: `WebSearchTool.enabled = False`，搜索返回空结果，Agent 依赖 LLM 内部知识
- **反思评估失败时**: 默认返回 `"satisfactory"`，不进行修订，直接输出当前方案
- **文件解析失败时**: 返回空字符串，Agent 忽略文件内容继续处理

---

## 八、初始化与启动

### 8.1 服务初始化顺序

`main.py` 中的初始化流程：

```python
# 1. 加载配置
config = load_config()  # YAML + .env + 环境变量

# 2. 初始化 LLM 服务
_llm = LLMService(
    base_url=config.llm.base_url,
    api_key_env=config.llm.api_key_env,
    model=config.llm.chat_model,
    temperature=config.llm.temperature,
    max_tokens=config.llm.max_tokens,
)

# 3. 初始化搜索客户端
_serper = SerperClient()

# 4. 注册工具
_tool_registry = ToolRegistry()
_tool_registry.register(WebSearchTool(_serper))
_tool_registry.register(FileParserTool())
_tool_registry.register(UserConfirmTool())
_tool_registry.register(SuggestQuestionsTool(_llm))
_tool_registry.register(FinishTool())

# 5. 初始化推理组件
_planner = MetaPlanner(_llm)
_agent = ReActAgent(llm=_llm, tool_registry=_tool_registry, ...)
_reflection_agent = ReflectionAgent(llm=_llm, react_agent=_agent)
```

### 8.2 Per-request 模型覆盖

每次请求可以覆盖默认的 LLM 模型和温度参数，创建独立的 LLM 实例和 Agent 实例：

```python
if request.model or request.temperature is not None:
    llm = LLMService(...)  # 创建新实例
    planner = MetaPlanner(llm)
    agent = ReActAgent(llm=llm, ...)
    reflection_agent = ReflectionAgent(llm=llm, react_agent=agent)
```

这允许同一服务同时支持不同模型（如 Qwen、DeepSeek-V4-Flash 等）的请求处理。
