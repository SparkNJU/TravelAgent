# TripAgent 启动指南

本文档给出本项目在 Windows 上的完整启动顺序，适用于首次启动和日常复现。

## 1. 启动 Agent 服务

目录：

`c:\Users\27126\Desktop\02\tripAgent_evalute\tripAgent\agent`

命令：

```powershell
Set-Location 'c:\Users\27126\Desktop\02\tripAgent_evalute\tripAgent\agent'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

验活地址：

`http://localhost:8000/health`

## 2. 启动 Backend 服务

目录：

`c:\Users\27126\Desktop\02\tripAgent_evalute\tripAgent\backend`

命令：

```powershell
Set-Location 'c:\Users\27126\Desktop\02\tripAgent_evalute\tripAgent\backend'
mvn spring-boot:run
```

验活地址：

`http://localhost:8080`

## 3. 启动 Frontend 服务

目录：

`c:\Users\27126\Desktop\02\tripAgent_evalute\tripAgent\frontend`

命令：

```powershell
Set-Location 'c:\Users\27126\Desktop\02\tripAgent_evalute\tripAgent\frontend'
npm.cmd install
npm.cmd run dev -- --host 0.0.0.0 --port 5173
```

访问地址：

`http://localhost:5173`

## 4. 推荐启动顺序

1. 先启动 Agent。
2. 再启动 Backend。
3. 最后启动 Frontend。

## 5. 常见问题

1. 如果 PowerShell 拒绝执行脚本，先运行：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

2. 如果 `.venv` 损坏，删除后重新创建：

```powershell
Remove-Item .venv -Recurse -Force
py -3 -m venv .venv
```

3. 如果 `pip.exe` 报 launcher 错误，统一改用：

```powershell
python -m pip install -r requirements.txt
```