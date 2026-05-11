# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Travel planning platform (旅行计划助手) — a three-tier application with an AI-powered travel agent, community/social features, and a separate Agent evaluation subsystem. UI and code comments are primarily in Chinese.

## Architecture

```
Frontend (Vue 3, :5173)
  │  /api/* (Vite proxy)
Backend (Spring Boot 4, :8080)
  │  HTTP to :8000
Agent (FastAPI, :8000)
  │  HTTPS
Google Serper API (search + images)
```

Backend → Agent bridge: `TripAssistantService` sends multipart form data to `app.agent.base-url` (default `http://localhost:8000/api/trip/plan`). Falls back to local `fallbackPlan()` if Agent is unreachable.

### Eval Sub-Project

`tripAgent_evalute/tripAgent/` is a **fully independent** project (Spring Boot 3.2.10 / Java 17, its own Vue 3 + TS frontend, LangChain-based agent, Docker Compose). Do not confuse it with the main project.

## Build & Run Commands

### Backend (Java 25, Maven)
```bash
cd backend
./mvnw spring-boot:run                     # run dev server
./mvnw -B package                           # build (tests skipped in CI)
./mvnw test                                 # run tests (only context-load test exists)
```
Requires MySQL on localhost:3306, database `travel_planning_db`. Init scripts: `backend/sql/init-database.sql` and `backend/src/main/resources/schema.sql`.

### Agent (Python 3.10)
```bash
cd agent
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```
Requires `SERPER_API_KEY` environment variable for web/image search.

### Frontend (Node 20+)
```bash
cd frontend
npm install
npm run dev        # dev server with proxy /api -> localhost:8080
npm run build      # production build
```

## Tech Stack

| Layer | Stack |
|-------|-------|
| Frontend | Vue 3, Vue Router 5, Vite 8, markdown-it + DOMPurify |
| Backend | Spring Boot 4.0.5, Spring Data JPA + Hibernate, MySQL 8.2, Lombok |
| Agent | FastAPI, Pydantic, Serper API, PyPDF2, python-docx |
| Eval | Spring Boot 3.2.10, Spring WebFlux (SSE), H2, LangChain |

## Backend Package Structure

`org.example.backend`:
- `controller/` — REST controllers (Auth, Travel, TripAssistant, Community, Profile)
- `service/` — Business logic (UserService, TravelPlanService, TripAssistantService, DestinationService, CommunityService)
- `entity/` — JPA entities (User, TravelPlan, PlanHighlight, Destination, CommunityPost, Comment)
- `dto/` — Request/response DTOs
- `repository/` — Spring Data JPA interfaces
- `config/` — CorsConfig

## Agent Module Structure

`agent/`:
- `main.py` — FastAPI endpoints (`/health`, `/api/trip/plan`)
- `models.py` — Pydantic request models
- `services/planner.py` — TripPlanner (regex-based destination extraction, markdown plan generation)
- `services/file_parser.py` — PDF/DOCX/text parsing
- `services/serper_client.py` — Google Serper API client

## Key API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/login` | User login |
| POST | `/api/auth/register` | User registration |
| GET | `/api/profile` | Get current user profile (X-User-Id header) |
| PUT | `/api/profile` | Update profile (username, email, phone, bio) |
| PUT | `/api/profile/password` | Change password |
| POST | `/api/profile/avatar` | Upload avatar (multipart/form-data) |
| GET | `/api/profile/avatars/{filename}` | Serve avatar image file |
| POST | `/api/assistant/chat` | AI travel planning (proxies to Agent) |
| POST | `/api/travel/plan/generate` | Mock plan generation |
| GET | `/api/travel/destinations/popular` | Popular destinations |
| CRUD | `/api/community/posts/**` | Community posts, comments, likes, images |

API documentation with full request/response examples: `docs/api.md`

## Database

MySQL `travel_planning_db` with JPA `ddl-auto=update`. Tables: `users`, `destinations`, `travel_plans`, `plan_highlights`, `orders`, `ai_planning_history`, `community_posts`, `comments`.

Default credentials in `application.properties`: root/123456.

### Schema Notes

- **Users table** has `profile_pic_url` (avatar) and `bio` fields
- **Community posts and comments** do NOT store `avatar`/`nickname`/`bio` — these are resolved at query time via `user_id` JOIN to `users`
- Response DTOs still return `username`, `avatar`, `bio` fields populated from the `users` table
- `images` column in `community_posts` is a JSON array stored as TEXT

## Auth & Guest Mode

- No Spring Security / JWT. Auth identity is passed via `X-User-Id` request header
- Frontend uses `useAuth` composable (`frontend/src/composables/useAuth.js`) for reactive auth state
- Login is a modal popup (not a standalone page). Users can browse as guests
- Guest users see community content but are prompted to login for: posting, commenting, liking, sharing
- `provide('showLoginModal')` from App.vue allows any deep component to trigger the login modal

## Git Remotes

| Name | URL | Purpose |
|------|-----|---------|
| origin | `ssh://172.29.4.49:8888/2026seiii-030-multi_agents/02.git` | GitLab (课程) |
| upstream | `https://github.com/SparkNJU/TravelAgent.git` | GitHub (部署源) |

推送到 master 时需要同步两边：
```bash
git push origin master && git push upstream master
```
如果一边有新提交，先 `git pull <remote> master --no-rebase` 合并再推。

## CI/CD

### CI (`.github/workflows/ci.yml`)
所有分支触发：Maven 构建、npm 构建、Python 语法检查。

### CD (`.github/workflows/deploy.yml`)
Push 到 master 自动部署到服务器 `121.41.202.221`（用户 `admin`），三个并行 job：

1. **deploy-frontend** — `npm run build` → rsync `dist/` → `/home/admin/myproject/frontend/`
2. **deploy-backend** — `mvn package` → rsync JAR → `/home/admin/myproject/backend/` → SSH 杀旧进程 + 启动新 JAR
3. **deploy-agent** — rsync `agent/` → `/home/admin/myproject/agent/`（排除 `.venv`、`__pycache__`、`.env`）→ SSH 重启 uvicorn

SSH 密钥存储在 GitHub Secret `SERVER_SSH_KEY`（ed25519，本地 `~/.ssh/github_actions_deploy`）。

服务器目录结构：
```
/home/admin/myproject/
├── frontend/    # Vue 静态文件 (Nginx :80 → /)
├── backend/     # Spring Boot JAR (:8080)
└── agent/       # FastAPI + .venv (:8000)
```

Nginx 路由：`/` → 前端静态文件，`/api/` → `:8080`，`/agent-api/` → `:8000`。

服务器已配置：MySQL、SERPER_API_KEY 环境变量、Agent Python 虚拟环境。
