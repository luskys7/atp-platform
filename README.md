# 自动化测试平台

详细需求与研发文档见 [自动化测试平台.md](./自动化测试平台.md)

## 快速启动（本地）

```powershell
Set-ExecutionPolicy -Scope Process Bypass
cd "e:\自动化测试平台-1"
.\scripts\start-local.ps1
```

详细分步命令、重启与健康检查见 [scripts/启动说明.md](./scripts/启动说明.md)。

- 前端：http://localhost:3000
- 后端：http://localhost:8080/api/health
- 账号：`admin` / `admin123`

## 环境要求

JDK 17+ · Node.js 18+ · Python 3.11+ · 无需 Docker

## 项目结构

| 目录 | 说明 |
|------|------|
| `backend-java/` | Spring Boot 调度中心（主后端） |
| `executor/` | Python 执行器 |
| `agent/` | Python 设备 Agent |
| `frontend/` | Vue3 管理后台 |
| `migrations/` | 数据库 SQL |
| `scripts/` | 启动脚本 |

完整 API、部署 SOP、模块实现状态见开发文档第 13、16~20 章。
