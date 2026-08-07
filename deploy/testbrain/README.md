# TestBrain + Milvus 独立部署（ATP 外挂）

与主平台 `docker-compose.yml` **隔离**。提供：

- Milvus Standalone（`:19530`）
- TestBrain Django（`:8000`）+ 专用 MySQL（宿主机映射 `:3307`）
- ATP 契约 API（overlay）：
  - `GET /api/v1/health`
  - `POST /api/v1/ai/generate-cases`（RAG + 标准用例 JSON）
- 原生知识库 API：`/api/add-knowledge/`、`/api/search-knowledge/`、`/api/knowledge-list/`

## 运行时：Podman Desktop（推荐，可替代 Docker Desktop）

本机可用 **Podman Desktop** 代替 Docker Desktop。Compose 文件仍为 `docker-compose.yml`（格式兼容），用 `podman compose` 启动即可。

### 前置

1. 安装并打开 [Podman Desktop](https://podman-desktop.io/)，确保 **Podman machine 已 Running**
2. 新开 PowerShell，确认：
   ```powershell
   podman --version
   podman compose version
   ```
3. 若提示找不到命令：在 Podman Desktop → Settings 中启用 CLI，或把 Podman 安装目录加入 PATH 后重开终端

### 快速启动

```powershell
cd C:\wq\atp-platform\atp-platform-main\atp-platform-main\deploy\testbrain
# .env 已配置时可跳过；否则：copy .env.example .env 后填写 QWEN_API_KEY

podman compose up -d --build
```

一键脚本（同目录）：

```powershell
.\start-podman.ps1
```

等价 Docker 命令（已装 Docker Desktop 时）：

```powershell
docker compose up -d --build
```

健康检查：

```powershell
curl http://127.0.0.1:8001/api/v1/health
curl http://127.0.0.1:9091/healthz
```

查看状态 / 日志：

```powershell
podman compose ps
podman compose logs -f testbrain
```

停服：

```powershell
podman compose down
# 清数据卷：podman compose down -v
```

> 说明：宿主机端口映射为 **8001→容器8000**（本机 overlay 定制版）。**当前 ATP 默认已切换为远程原版 TestBrain：`http://10.0.98.20:8000`**（`TESTBRAIN_URL` / Vite `TESTBRAIN_URL`）。

## 平台内嵌入（侧栏）

稳妥方式：**TestBrain 仍独立部署**，ATP 前端通过同域反代嵌入。

1. 侧栏 **「AI 测试助手」→ TestBrain**（路由 `/testbrain`）一点即嵌原生首页，无需新窗口
2. Vite 将页面内资源前缀 `/testbrain/*` 反代到 `http://10.0.98.20:8000`
3. 生产网关需配置同等反代。

### 常见问题

| 现象 | 处理 |
|------|------|
| `podman` 无法识别 | `$env:Path = "C:\Program Files\RedHat\Podman;" + $env:Path` 后重试 |
| `looking up compose provider failed` | 已用 pip 安装 `podman-compose`；把 `%APPDATA%\Python\Python311\Scripts` 加入 PATH，或直接跑 `.\start-podman.ps1` |
| `registry-1.docker.io ... i/o timeout` | 国内直连 Docker Hub 失败。本目录 compose/Dockerfile **已默认 DaoCloud 镜像**；重新 `podman-compose up -d --build` 即可 |
| 构建卡在 `git clone ... Cloning into '/app'` | **已规避**：源码在 `vendor/TestBrain`（本机已下载）。`Ctrl+C` 后重新 `podman-compose up -d --build`，构建不再 clone |
| 构建慢 / 拉镜像失败 | 换镜像：编辑 `docker-compose.yml` 前缀；或复制 `registries.conf.example` → `%USERPROFILE%\.config\containers\registries.conf` |
| 端口被占用 | 改 `docker-compose.yml` 端口映射，或停掉占用 8000/19530/3307 的进程 |

入库示例：

```powershell
curl -X POST http://127.0.0.1:8000/api/add-knowledge/ `
  -H "Content-Type: application/json" `
  -d "{\"title\":\"示例PRD\",\"content\":\"打开App选择机器人下发任务...\"}"
```

生成示例（ATP 契约）：

```powershell
curl -X POST http://127.0.0.1:8000/api/v1/ai/generate-cases `
  -H "Content-Type: application/json" `
  -d "{\"requirement\":\"打开App选择机器人下发任务并查看状态\",\"platform\":\"android\",\"max_cases\":5,\"schema\":\"atp_standard_case_v1\"}"
```

## 平台内嵌入（侧栏）

稳妥方式：**TestBrain 仍独立部署**，ATP 前端通过同域反代嵌入。

1. 侧栏 **「AI 测试助手」**
   - 用例生成 → `/ai-cases`（平台自有页）
   - 知识库 → `/ai-knowledge`（平台自有页，调 ATP→TestBrain API）
   - TestBrain 工作台 → `/ai-testbrain`（iframe 嵌入 `/testbrain/*`）
2. Vite 开发代理：`/testbrain` → `http://10.0.98.20:8000`
3. TestBrain 宿主机端口：**8001**（避免与本机其它 8000 冲突）

生产环境请在网关增加同等反代规则。

## 说明

- 默认 Embedding：`EMBEDDING_PROVIDER=aliyun`（`text-embedding-v4`，维度 1024）
- 默认 LLM：`LLM_DEFAULT_PROVIDER=qwen`
- 不改动 ATP 自动化执行 / 设备 / 录制底层
- Compose 文件名仍为 `docker-compose.yml`（Podman / Docker 通用）
