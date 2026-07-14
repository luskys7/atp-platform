@echo off
chcp 65001 >nul
echo === TestFlow 部署 ===
echo.

where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Docker，无法使用 Docker 部署。
    echo.
    echo 请选择以下方式之一:
    echo   1. 安装 Docker Desktop: https://www.docker.com/products/docker-desktop/
    echo   2. 运行 scripts\check-env.bat 查看完整环境检测结果
    echo   3. 仅启动前端: cd frontend ^&^& npm install ^&^& npm run dev
    echo.
    pause
    exit /b 1
)

echo [1/3] 构建并启动所有服务...
docker compose up -d --build
if %errorlevel% neq 0 (
    echo.
    echo [错误] 部署失败，常见原因:
    echo   - Docker Desktop 未启动
    echo   - 端口 80/8080/3306 被占用
    echo   - 网络无法拉取镜像
    echo.
    echo 查看日志: docker compose logs backend
    pause
    exit /b 1
)

echo [2/3] 等待服务就绪...
timeout /t 15 /nobreak >nul

echo [3/3] 检查服务状态...
docker compose ps

echo.
echo === 部署完成 ===
echo 前端地址: http://localhost
echo 后端 API: http://localhost:8080/api/health
echo 默认账号: admin / admin123
echo.
echo 若后端未就绪，执行: docker compose logs -f backend
pause
