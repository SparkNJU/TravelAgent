#!/bin/bash

# 定义路径
PROJECT_DIR="/home/admin/myproject"
BACKEND_DIR="$PROJECT_DIR/backend"

echo "====== 开始自动识别并部署后端服务 ======"

# 进入后端目录
cd $BACKEND_DIR || { echo "❌ 目录不存在"; exit 1; }

# 💡 核心升级 1：动态获取最新的 jar 包
# ls -t 会按修改时间排序（最新的在最上面），head -n 1 只取第一行
JAR_NAME=$(ls -t *.jar 2>/dev/null | head -n 1)

# 防御性判断：如果文件夹里根本没有 jar 包，直接报错退出
if [ -z "$JAR_NAME" ]; then
    echo "❌ 错误: 在 $BACKEND_DIR 目录下没有找到任何 .jar 文件！请先上传！"
    exit 1
fi

echo "📦 智能识别到最新部署包: [ $JAR_NAME ]"

# 💡 核心升级 2：杀掉旧进程
echo "➡️ 检查旧版本进程..."
# 这里不按具体的包名查了，直接查这台服务器上运行的 "java -jar" 程序
PID=$(ps -ef | grep "java -jar" | grep -v grep | awk '{ print $2 }')

if [ -z "$PID" ]; then
    echo "   没有发现正在运行的旧进程。"
else
    kill -9 $PID
    echo "   已成功杀掉旧进程 (PID: $PID)。"
fi

# 3. 启动最新识别到的 jar 包
echo "➡️ 正在后台启动新版本..."

# 启动程序并将日志输出到 app.log
nohup java -jar $JAR_NAME > app.log 2>&1 &

echo "✅ 部署完成！后端已静默运行。"
echo "📄 查看启动日志请运行: tail -f $BACKEND_DIR/app.log"
echo "========================================"
