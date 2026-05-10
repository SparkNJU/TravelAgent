#!/bin/bash
PROJECT_DIR="/home/admin/myproject/agent"

echo "====== 开始更新 Python Agent 服务 ======"
cd $PROJECT_DIR

# 1. 杀掉旧的 uvicorn 进程
PID=$(ps -ef | grep "uvicorn main:app" | grep -v grep | awk '{ print $2 }')
if [ ! -z "$PID" ]; then
    kill -9 $PID
    echo "   已杀掉旧进程 (PID: $PID)"
fi

# 2. 启动新服务 (这里有一个隐藏的 Linux 大神技巧)
# 我们不写 source 激活环境，而是直接使用虚拟环境里的完整路径去调用 uvicorn！
echo "➡️ 正在后台启动新服务..."
nohup /home/admin/myproject/agent/.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000 > agent.log 2>&1 &

echo "✅ Python 服务已在后台运行！"
echo "📄 查看日志请敲: tail -f $PROJECT_DIR/agent.log"
echo "=============================="
