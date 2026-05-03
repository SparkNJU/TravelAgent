# 02 迭代二 - 多智能体旅行规划平台

本仓库包含一个可本地联调的完整系统：
- `frontend`：Vue 3 + Vite 前端页面（登录、注册、AI 规划工作台）
- `backend`：Spring Boot 后端 API（用户、目的地、规划、Agent 桥接）
- `agent`：FastAPI 智能体服务（解析输入、联网搜索、生成 Markdown 行程）

## 1. 仓库结构

```text
02/
	README.md                 # 当前文档（总览 + 快速启动）
	frontend/                 # 前端工程
	backend/                  # Java 后端工程
	agent/                    # Python Agent 工程
	StudyAgent-main/          # 其他实验目录（本次联调不依赖）
```

## 2. 架构关系

请求链路如下：

1. 浏览器访问 `frontend`（默认 `http://localhost:5173`）
2. 前端通过 Vite 代理把 `/api/*` 转发到 `backend`（`http://localhost:8080`）
3. `backend` 在 `TripAssistantService` 中调用 `agent` 接口（默认 `http://localhost:8000/api/trip/plan`）
4. `agent` 返回规划结果（标题、目的地、天数、Markdown、图片、来源）

## 3. 环境要求

- Node.js：`^20.19.0 || >=22.12.0`
- npm：建议 `>=10`
- Java：`17`
- Maven：可直接使用仓库自带 `mvnw.cmd`
- Python：建议 `3.10+`
- MySQL：建议 `8.x`

## 4. 推荐启动顺序（开发联调）

从仓库根目录 `02` 打开 4 个终端，按顺序执行。

### Step 1. 初始化数据库

先在 MySQL 执行建库脚本：

```sql
SOURCE backend/sql/init-database.sql;
```

脚本会创建 `travel_planning_db`。

### Step 2. 启动 Agent

```powershell
cd agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:SERPER_API_KEY="your_serper_key"
uvicorn main:app --host 0.0.0.0 --port 8000
```

说明：
- 不设置 `SERPER_API_KEY` 也能运行，但不会联网搜索图片/网页来源。
- 健康检查：`http://localhost:8000/health`

### Step 3. 启动 Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

说明：
- 数据库连接在 `backend/src/main/resources/application.properties`
- 需要确认账号密码和本地 MySQL 一致（默认是 `root/123456`）
- 当前配置 `spring.jpa.hibernate.ddl-auto=create-drop`，重启会重建表并清空数据

### Step 4. 启动 Frontend

```powershell
cd frontend
npm install
npm run dev
```

默认访问：`http://localhost:5173`

## 5. 快速验证联调是否成功

1. 打开前端，先注册新用户
2. 登录后进入首页，再进入 AI 规划工作台
3. 输入需求（如“东京 5 天，美食+城市观光”）并提交
4. 页面成功展示行程卡片、Markdown 内容，且可看到图片/来源（有 `SERPER_API_KEY` 时）

如果 Agent 未启动，后端会返回降级草案（不是 500 崩溃），便于前端继续调试。

## 6. 分模块文档

- 前端说明：`frontend/README.md`
- 后端说明：`backend/README.md`
- Agent 说明：`agent/README.md`

## 7. 常见问题

- 端口冲突：检查 `5173`、`8080`、`8000` 是否被占用
- 数据库连接失败：确认 MySQL 已启动且 `application.properties` 凭据正确
- 前端接口 404/502：确认后端已启动，且 `frontend/vite.config.js` 的 `/api` 代理仍指向 `8080`
- Agent 无联网结果：确认 `SERPER_API_KEY` 已设置且有效

