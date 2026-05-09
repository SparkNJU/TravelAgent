# Agent - Travel Assistant Service

FastAPI 智能体服务，负责解析用户需求、可选解析上传文件、可选联网搜索，并利用 ReAct Agent 和 MetaPlanner 架构流式 (SSE) 生成旅行计划等回答。

## 1. 技术栈

- Python 3.10+
- FastAPI
- Uvicorn
- Pydantic
- dotenv
- LangChain / LLM Service APIs

## 2. 模块架构

```text
agent/
   main.py                   # FastAPI 入口，暴露路由及流式接口
   models.py                 # 请求模型 AgentChatRequest
   services/
      file_parser.py          # 文件解析工具
      planner.py              # 元规划器，拆解任务目标
      react_agent.py          # ReAct 智能体，推理并调用工具
      serper_client.py        # Serper 搜索引擎
      weather_client.py       # 天气搜索工具
      tool_registry.py        # 工具注册中心
      sse_events.py           # SSE 格式辅助
```

核心流程：
1. `POST /api/agent/chat` 接收大模型交流请求
2. 内部根据请求的 `mode` 与 `generate_plan_first` 判断是否调用 `planner` 来规划步骤。
3. `ReActAgent` 根据用户提问和背景信息自动进行多轮 Tool 调用（搜索、读文件、查天气等）。
4. 将过程中的 thought, tool_call, tool_result, 以及最终 message 用 **SSE 流**的形式不断推给客户端（通常是 Backend）。

## 3. 环境准备

在 `agent` 目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

复制配置文件并编辑环境变量（如果需要）：
通常我们需要在 `agent` 目录新建一个 `.env` 文件来存储敏感 Key 配置。

## 4. 启动服务

```powershell
uvicorn main:app --host 0.0.0.0 --port 8000
```
或直接进入虚拟环境运行：
```powershell
uvicorn main:app --reload
```

默认地址：`http://localhost:8000`

健康检查：
- `GET /health`

## 5. 接口契约

目前详细的 API 定义及 Local Curl 测试用例，请查阅 [API 文档](./docs/api.md)。

核心的聊天接口为 **Server-Sent Events (SSE)** 流式接口：
### `POST /api/agent/chat`

请求示例：

```json
{
   "query": "帮我做一个东京5天旅行计划，偏美食和城市观光",
   "user_id": 1,
   "mode": "agent",
   "generate_plan_first": true
}
```

## 6. 与后端联调

后端通常通过流式调用的方式与 Agent 交互，直接代理或接收此端点吐出的事件。
如果遇到不可用或不通的情况，请检查：
1. Agent 进程是否已启动且没有报错
2. 端口 `8000` 是否被占用，是否存在跨域需求等
3. `health` 接口是否返回正确的 `tools_registered` 列表。

## 7. 常见问题

- `ModuleNotFoundError`：确认已激活 `.venv` 且执行过 `pip install -r requirements.txt`

- `SERPER_API_KEY` 无效：联网结果为空，先用无联网模式验证主流程
- 上传文件解析为空：请检查文件格式和编码（支持 txt/pdf/docx）
