# Frontend - Travel Planning Web App

Vue 3 + Vite 前端应用，提供登录、注册、首页和 AI 旅行规划工作台。

## 1. 技术栈

- Vue 3
- Vue Router
- Vite
- markdown-it + DOMPurify（渲染与净化 Markdown）

## 2. 路由与页面架构

```text
src/
  main.js
  App.vue
  router/index.js
  views/
    LoginView.vue
    RegisterView.vue
    HomeView.vue
    AIPlanView.vue
```

页面路由：
- `/login`：登录
- `/register`：注册
- `/`：首页（需要本地 token）
- `/ai-plan`：AI 工作台（需要本地 token）

鉴权方式：
- 前端在 `localStorage` 中保存 `token/userId/username`
- 通过路由守卫限制未登录用户访问首页与 AI 页面

## 3. 环境要求

- Node.js：`^20.19.0 || >=22.12.0`
- npm：建议 `>=10`

## 4. 安装与启动

在 `frontend` 目录执行：

```powershell
npm install
npm run dev
```

默认访问：`http://localhost:5173`

其他命令：

```powershell
npm run build
npm run preview
```

## 5. 与后端联调

`vite.config.js` 已配置代理：
- `/api` -> `http://localhost:8080`

因此前端发起请求时使用相对路径即可，例如：
- `/api/auth/login`
- `/api/auth/register`
- `/api/assistant/chat`

联调前请先启动：
1. Agent（8000）
2. Backend（8080）
3. Frontend（5173）

## 6. 主要交互流程

1. 注册新用户
2. 登录后进入首页
3. 打开 AI 工作台
4. 输入需求，可选上传文件
5. 查看返回的行程 Markdown、图片和来源

## 7. 常见问题

- 启动报 Node 版本不兼容：升级到 README 要求版本
- 前端请求失败：检查后端是否在 `8080` 运行
- AI 规划无结果：检查后端和 Agent 是否都已启动
