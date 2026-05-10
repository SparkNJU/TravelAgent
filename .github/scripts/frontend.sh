#!/bin/bash
FRONTEND_DIR="/home/admin/myproject/frontend"

echo "====== 验证前端部署 ======"
if [ -f "$FRONTEND_DIR/index.html" ]; then
    echo "✅ index.html 存在，前端部署成功"
else
    echo "❌ index.html 未找到"
    exit 1
fi
echo "========================================"
