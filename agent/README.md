# Agent - Travel Assistant Service

FastAPI 智能体服务，负责解析用户需求、可选解析上传文件、可选联网搜索，并生成 Markdown 旅行计划。

## 1. 技术栈

- Python 3.10+
- FastAPI
- Uvicorn
- Pydantic
- requests
- PyPDF2 / python-docx（解析上传文档）

## 2. 模块架构

```text
agent/
   main.py                   # FastAPI 入口，暴露路由
   models.py                 # 请求模型 TripPlanRequest
   services/
      file_parser.py          # 文件解析（txt/pdf/docx）
      planner.py              # 目的地/天数提取 + Markdown 生成
      serper_client.py        # Serper 搜索与图片接口
```

核心流程：
1. `POST /api/trip/plan` 接收请求
2. `file_parser` 解析上传文件（可选）
3. `planner` 生成旅行计划
4. 如果配置 `SERPER_API_KEY`，附加搜索来源和图片结果

## 3. 环境准备

在 `agent` 目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

可选环境变量：

```powershell
$env:SERPER_API_KEY="your_serper_api_key"
```

说明：
- 不设置 `SERPER_API_KEY` 时服务仍可用，但 `sources/images` 可能为空

## 4. 启动服务

```powershell
uvicorn main:app --host 0.0.0.0 --port 8000
```

默认地址：`http://localhost:8000`

健康检查：
- `GET /health`

## 5. 接口契约

### `POST /api/trip/plan`

请求示例：

```json
{
   "query": "帮我做一个东京5天旅行计划，偏美食和城市观光",
   "user_id": 1,
   "file_name": "notes.txt",
   "file_base64": "...",
   "file_mime_type": "text/plain"
}
```

响应关键字段：
- `title`
- `destination`
- `days`
- `summary`
- `markdown`
- `images`
- `sources`

## 6. 与后端联调

后端通过 `app.agent.base-url` 调用本服务，默认值：

`http://localhost:8000/api/trip/plan`

若后端提示 Agent 不可用，请检查：
1. Agent 进程是否已启动
2. 端口 `8000` 是否被占用
3. 后端配置中的 `app.agent.base-url` 是否正确

## 7. 常见问题

- `ModuleNotFoundError`：确认已激活 `.venv` 且执行过 `pip install -r requirements.txt`
- `SERPER_API_KEY` 无效：联网结果为空，先用无联网模式验证主流程
- 上传文件解析为空：请检查文件格式和编码（支持 txt/pdf/docx）
