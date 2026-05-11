# TravelMind 旅行规划平台

本项目包含一个基于大语言模型（LLM）的旅行规划 Agent 主应用，以及配套的评测系统。私服 GitLab 和 GitHub 是保持同步的，项目在 GitHub Actions 上进行 CI/CD 持续集成，并且目前已经持续部署，可以直接访问试用。

## 📍 项目地址与在线体验

- **GitHub 镜像**：[https://github.com/SparkNJU/TravelAgent.git](https://github.com/SparkNJU/TravelAgent.git)
- **校内 GitLab**：[http://172.29.4.49/2026seiii-030-multi_agents/02.git](http://172.29.4.49/2026seiii-030-multi_agents/02.git) (主库)
- **在线即刻体验**：[http://121.41.202.221/](http://121.41.202.221/) (已自动部署可直接试用)

## 📁 仓库结构与模块索引

项目当前划分为两个主要部分：**旅行计划 Agent 应用** 和 **评估平台**。

### 1. 旅行计划 Agent 应用
业务的主系统采用三端分离架构：
- **[Agent](./agent/README.md)**：FastAPI 智能体服务（大模型规划与联网检索的核心）。
- **[Backend](./backend/README.md)**：Spring Boot 后端 API（负责用户管理、状态流转和数据存储）。
- **[Frontend](./frontend/README.md)**：Vue 3 + Vite 前端架构（用户登录注册、AI 聊天及可视化旅行规划工作台）。

### 2. 评估（Evaluate）平台 Agent 应用
- **[tripAgent_evalute](./tripAgent_evalute/)**：提供评估平台的 Agent 应用模块，与主应用类似，其中也同样拆分为了 `agent`、`backend` 和 `frontend` 三端。

---

## 🚀 快速本地环境拉起（主应用为例）

如果需要在本地调试主项目，核心联调流程如下（推荐从根目录开启 4 个终端执行）：

### Step 1. 初始化数据库

由于 MySQL 数据源由 Backend 连接，先在本地执行建库脚本：
```sql
SOURCE backend/sql/init-database.sql;
```

### Step 2. 启动 Agent
```powershell
cd agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:SERPER_API_KEY="your_serper_key"  # 开启大模型和联网需进行配置
uvicorn main:app --host 0.0.0.0 --port 8000
```

### Step 3. 启动 Backend
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### Step 4. 启动 Frontend
```powershell
cd frontend
npm install
npm run dev
```
之后访问默认地址 [http://localhost:5173](http://localhost:5173) 进行联调测试。

## ❓ 常见问题答疑

- **Agent 无法联网 / 生成图片**：请检查是否有效配置了环境变量 `$env:SERPER_API_KEY`。如果没有启动 Agent 或者请求异常失败，后端能够降级以防崩溃。
- **前后端不通 (404/502)**：请确认 Backend 已经启动在了 `8080` 端口，并确认 `frontend/vite.config.js` 的代理地址仍然完好。
- **端口冲突卡死**：确保本机暂未占用 `5173`、`8080`、`8000` 端口。

