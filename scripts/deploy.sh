#!/bin/bash
set -e

echo "=== TestFlow 部署 ==="

if ! command -v docker &> /dev/null; then
    echo "错误: 请先安装 Docker"
    exit 1
fi

echo "[1/3] 构建并启动所有服务..."
docker-compose up -d --build

echo "[2/3] 等待服务就绪..."
sleep 10

echo "[3/3] 检查服务状态..."
docker-compose ps

echo ""
echo "=== 部署完成 ==="
echo "前端地址: http://localhost"
echo "后端 API: http://localhost:8080/api/health"
echo "MinIO 控制台: http://localhost:9001"
echo "默认账号: admin / admin123"
echo ""
echo "设备接入步骤:"
echo "  1. 登录管理后台，在设备管理中添加白名单"
echo "  2. 在测试服务器上运行 Agent: cd agent && go run ."
echo "  3. 设备将自动注册并出现在设备列表中"
