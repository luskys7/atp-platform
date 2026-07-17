# 自动化测试平台

详细需求与研发文档见 [自动化测试平台.md](./自动化测试平台.md)

## 快速启动（本地）

在项目根目录执行（必须是含 `scripts/` 的那一层）：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
cd "c:\Users\yu.xiao03\UI_AutoTest\atp-platform-main\atp-platform-main"
.\scripts\start-local.ps1
```

| 方式 | 命令 |
|------|------|
| 一键启动 | `.\scripts\start-local.ps1` 或双击 `scripts\start-local.bat` |
| 一键停止 | `.\scripts\stop-local.ps1` 或双击 `scripts\stop-local.bat` |

脚本会：释放旧端口 → 清 H2 锁 → 起后端并**等到健康检查通过** → 起执行器 → 起前端。

- 前端：http://localhost:3000  
- 后端：http://localhost:8080/api/v1/health  
- 账号：`admin` / `admin123`

远程访问者本机接设备：`.\scripts\start-remote-executor.ps1`（见 [启动说明.md](./启动说明.md)）。

## 环境要求

JDK 17+ · Node.js 18+ · Python 3.11+ · Maven（项目自带 `tools/apache-maven-3.9.6`）· 无需 Docker

## 项目结构

| 目录 | 说明 |
|------|------|
| `backend-java/` | Spring Boot 调度中心（主后端） |
| `executor/` | Python 执行器 |
| `agent/` | Python 设备 Agent |
| `frontend/` | Vue3 管理后台 |
| `migrations/` | 数据库 SQL |
| `scripts/` | 启动 / 停止脚本 |

完整 API、部署 SOP、模块实现状态见开发文档第 13、16~20 章。
