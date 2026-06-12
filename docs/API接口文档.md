# Agent 端 API 文档

Agent 模块提供基于 FastAPI 的智能体聊天接口，支持 Server-Sent Events (SSE) 流式输出，可接入大语言模型 (LLM) 并使用搜索等工具。

## 服务信息
- **Base URL**: `http://127.0.0.1:8000`
- **框架**: FastAPI

## API 列表

### 1. 健康检查 (Health Check)
用于检测服务是否正常运行，及启用的工具。

- **URL**: `/health`
- **Method**: `GET`
- **Content-Type**: `application/json`

**成功响应示例:**
```json
{
  "ok": true,
  "serper_enabled": true,
  "tools_registered": [
    "web_search",
    "weather_search",
    "parse_file"
  ]
}
```

### 2. 智能体对话接口 (Agent Chat)
用于像流式对话一样返回规划过程及模型回复的数据流。

- **URL**: `/api/agent/chat`
- **Method**: `POST`
- **Content-Type**: `application/json`

#### 请求体参数 (`AgentChatRequest`)

| 字段 | 类型 | 必填 | 默认值 | 描述 |
| :--- | :--- | :--- | :--- | :--- |
| `query` | `string` | 是 | - | 用户的自然语言查询请求 |
| `user_id` | `int` | 否 | `1` | 用户ID |
| `mode` | `string` | 否 | `"agent"` | 运行模式，可选 `"plan"` 或 `"agent"` |
| `generate_plan_first`| `boolean`| 否 | `true` | 是否先调用 planner 生成拆解步骤计划 |
| `model` | `string` / `null` | 否 | `null` | 使用的 LLM 模型名 |
| `temperature` | `float` / `null` | 否 | `null` | LLM 的 temperature |
| `file_name` | `string` / `null` | 否 | `null` | 用户上传的文件名 |
| `file_base64` | `string` / `null` | 否 | `null` | 文件的 Base64 编码 |
| `file_mime_type` | `string` / `null`| 否 | `null` | 文件的 MIME 类型 |

#### 响应说明
响应内容为 **Server-Sent Events (SSE)** 流。格式为：

```text
data: {"type": "event_type", "content": "chunk data", "metadata": {}}

data: {"type": "event_type", ...}

...
data: [DONE]
```

**响应数据流包含如下常见的事件类型 (`type`)：**
- `plan`: 计划生成过程及中间输出。
- `thought`: Agent 的思考过程。
- `tool_call`: 触发工具调用。
- `tool_result`: 工具返回的结果。
- `message`: 最终用户可见的回复内容块。
- `error`: 服务端错误信息。
- `[DONE]`: 代表数据流结束的标志事件。

---

## 本地 curl 测试命令

首先确保你的服务通过 `uvicorn main:app --host 127.0.0.1 --port 8000` 启动了并在运行。

### 1. 测试 /health 接口
```bash
curl -X GET http://127.0.0.1:8000/health
```

### 2. 测试 /api/agent/chat 接口 (常规 Agent 对话模式)
```bash
curl -N -X POST http://127.0.0.1:8000/api/agent/chat \
-H "Content-Type: application/json" \
-d '{"query": "帮我做一份南京的一日游攻略", "mode": "agent", "generate_plan_first": true}'
```
> 注意：这里使用了 `-N` 或 `--no-buffer` 参数来确保能在控制台上实时打印不断推过来的服务器事件流 (SSE 数据)。

### 3. 测试 /api/agent/chat 接口 (仅使用 planner 生成计划模式)
```bash
curl -N -X POST http://127.0.0.1:8000/api/agent/chat \
-H "Content-Type: application/json" \
-d '{"query": "分析一下如何规划一场上海的三天旅行", "mode": "plan"}'
```
