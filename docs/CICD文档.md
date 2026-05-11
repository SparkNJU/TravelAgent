# CI/CD 部署文档

## 1. 概述

本项目使用 GitHub Actions 实现持续集成与持续部署。代码同时推送到 GitLab（课程仓库）和 GitHub（部署源），push 到 `master` 分支时自动触发部署。

### 架构总览

```
开发者本地
  │  git push origin master (GitLab)
  │  git push upstream master (GitHub)
  ▼
GitHub Actions
  ├── CI Workflow (ci.yml)  — 所有分支触发，构建检查
  └── CD Workflow (deploy.yml) — 仅 master 分支触发，自动部署
        ├── deploy-frontend → rsync dist/ → 服务器
        ├── deploy-backend  → rsync JAR → 服务器 → SSH 重启
        └── deploy-agent    → rsync agent/ → 服务器 → SSH 重启
  ▼
服务器 121.41.202.221 (admin)
  ├── Nginx (:80)  → 静态文件 / 反向代理
  ├── Backend (:8080) — Spring Boot JAR
  └── Agent (:8000) — FastAPI + uvicorn
```

## 2. Git 远程源配置

| 名称 | 地址 | 用途 |
|------|------|------|
| origin | `ssh://172.29.4.49:8888/2026seiii-030-multi_agents/02.git` | GitLab 课程仓库 |
| upstream | `https://github.com/SparkNJU/TravelAgent.git` | GitHub 部署源 |

### 同步推送

每次提交到 master 后需同步两边：

```bash
git push origin master && git push upstream master
```

如果远端有新提交，先拉取合并：

```bash
git pull origin master --no-rebase
git pull upstream master --no-rebase
```

## 3. 服务器环境

### 基本信息

- **IP**: 121.41.202.221
- **用户**: admin
- **系统**: Ubuntu 24.04.2 LTS
- **SSH 登录**: 公钥认证（ed25519）

### 目录结构

```
/home/admin/myproject/
├── frontend/       # Vue 3 静态文件（由 CI 自动同步）
├── backend/        # Spring Boot JAR（由 CI 自动上传）
└── agent/          # Python 代码 + .venv（由 CI 自动同步）
    ├── .venv/      # Python 虚拟环境（服务器本地，CI 不覆盖）
    ├── .env        # 环境变量（服务器本地，CI 不覆盖）
    ├── main.py
    ├── models.py
    ├── services/
    ├── config/
    └── requirements.txt
```

### Nginx 配置

```nginx
server {
    listen 80;
    server_name _;

    # 前端 Vue
    location / {
        root /home/admin/myproject/frontend;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # Agent FastAPI
    location /agent-api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 服务器已预配置

- MySQL 8.2（`localhost:3306`，数据库 `travel_planning_db`）
- `SERPER_API_KEY` 环境变量
- Agent Python 虚拟环境（`/home/admin/myproject/agent/.venv`）

## 4. 触发机制

所有 Workflow 仅由 **GitHub（upstream）仓库** 的 push/PR 事件触发，推送到 GitLab（origin）不会触发 GitHub Actions。

| 推送目标 | 触发的 Workflow | Job 数量 |
|----------|----------------|---------|
| 非 master 分支 | CI (`ci.yml`) | 3 个（代码构建检查） |
| master 分支 | CI (`ci.yml`) + CD (`deploy.yml`) | **6 个**（3 个构建检查 + 3 个部署） |

## 5. CI Workflow（持续集成）

**文件**: `.github/workflows/ci.yml`

| Job | 内容 |
|-----|------|
| Backend - Maven build | Java 17 + Maven 构建（跳过测试） |
| Frontend - Node build | Node 20 + npm 构建 |
| Agent - Python install & check | Python 3.10 + 依赖安装 + 语法检查 |

## 6. CD Workflow（持续部署）

**文件**: `.github/workflows/deploy.yml`

### 6.1 deploy-frontend

| 步骤 | 说明 |
|------|------|
| Checkout | 拉取代码 |
| Node 20 + npm | `npm ci && npm run build` |
| rsync 同步 | `frontend/dist/` → `/home/admin/myproject/frontend/`（`--delete` 清理旧文件） |
| 验证 | SSH 检查 `index.html` 是否存在 |

### 6.2 deploy-backend

| 步骤 | 说明 |
|------|------|
| Checkout | 拉取代码 |
| Java 17 + Maven | `mvn -B -DskipTests package` |
| rsync 同步 | `backend-0.0.1-SNAPSHOT.jar` → `/home/admin/myproject/backend/` |
| SSH 重启 | 杀掉旧 `java -jar` 进程 → `nohup java -jar` 启动新版本 |

### 6.3 deploy-agent

| 步骤 | 说明 |
|------|------|
| Checkout | 拉取代码 |
| rsync 同步 | `agent/` → `/home/admin/myproject/agent/`（排除 `.venv`、`__pycache__`、`.env`） |
| SSH 重启 | 杀掉旧 `uvicorn` 进程 → 用服务器 `.venv/bin/uvicorn` 启动 |

三个 job **并行执行**，互不依赖。

## 7. SSH 密钥配置

### 7.1 生成密钥对

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github_actions_deploy
# 提示输入密码时直接回车（不设密码）
```

### 7.2 添加公钥到服务器

如果服务器允许密码登录：

```bash
ssh-copy-id -i ~/.ssh/github_actions_deploy.pub admin@121.41.202.221
```

如果服务器仅允许公钥登录（通过 root 操作）：

```bash
ssh root@121.41.202.221  # 密码登录
mkdir -p /home/admin/.ssh
echo "公钥内容" >> /home/admin/.ssh/authorized_keys
chown -R admin:admin /home/admin/.ssh
chmod 700 /home/admin/.ssh
chmod 600 /home/admin/.ssh/authorized_keys
```

### 7.3 配置 GitHub Secret

1. 查看私钥：`cat ~/.ssh/github_actions_deploy`
2. GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret
3. Name: `SERVER_SSH_KEY`，Value: 粘贴私钥全文

### 7.4 验证

```bash
ssh -i ~/.ssh/github_actions_deploy admin@121.41.202.221
```

## 8. 手动操作

### 重启后端

```bash
ssh admin@121.41.202.221
cd /home/admin/myproject/backend
PID=$(ps -ef | grep "java -jar" | grep -v grep | awk '{ print $2 }')
[ ! -z "$PID" ] && kill -9 $PID
JAR_NAME=$(ls -t *.jar | head -n 1)
nohup java -jar $JAR_NAME > app.log 2>&1 &
```

### 重启 Agent

```bash
ssh admin@121.41.202.221
PID=$(ps -ef | grep "uvicorn main:app" | grep -v grep | awk '{ print $2 }')
[ ! -z "$PID" ] && kill -9 $PID
cd /home/admin/myproject/agent
nohup .venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000 > agent.log 2>&1 &
```

### 查看日志

```bash
tail -f /home/admin/myproject/backend/app.log   # 后端日志
tail -f /home/admin/myproject/agent/agent.log    # Agent 日志
```

## 9. 故障排查

| 问题 | 排查方法 |
|------|---------|
| GitHub Actions 部署失败 | 检查 Actions 页面日志，常见原因：SSH 连接失败（检查 Secret）、构建失败（检查代码） |
| SSH 连接被拒 | 验证密钥：`ssh -i ~/.ssh/github_actions_deploy admin@121.41.202.221` |
| 前端页面 404 | 检查 Nginx 配置和 `/home/admin/myproject/frontend/index.html` 是否存在 |
| 后端 API 无响应 | `curl http://localhost:8080/api/travel/destinations/popular`，检查 JAR 是否启动 |
| Agent 无响应 | `curl http://localhost:8000/health`，检查 uvicorn 进程和日志 |
| rsync 权限错误 | 确认 admin 用户对 `/home/admin/myproject/` 有写权限 |
| Git push 冲突 | 先 `git pull <remote> master --no-rebase` 合并再推 |
