# AI 用例生成接入说明（独立外挂）

## 产品边界

本模块是挂在自动化测试平台上的**独立外挂**：

- 产出：**标准功能测试用例**（标题 `tc-p0~p3`、前置条件、步骤+预期等）
- 主路径：本页输入/上传/Confluence →（可选）同步知识库 → 生成 → 本页预览 →（可选）单向落库草稿
- **不与**设备、录制、套件、公共组件、执行调度互通
- **不改动**执行引擎与拾取底层

相对《TestBrain平台接入自研自动化测试平台》报告：

| 报告项 | 决策 |
|--------|------|
| TestBrain + Milvus 独立部署与 RAG 知识库 | **优先完善**（见 [deploy/testbrain](../deploy/testbrain/README.md)） |
| 文本用例 → 可执行自动化步骤适配 | 不做（与现行定位冲突） |
| AI 用例深度评审服务（TestBrain 评审） | 暂缓；本页仅做字段完整性软检查 |
| SSO/权限专项映射、独立监控体系 | 暂缓（沿用平台登录即可） |

## 现行能力

- LLM（千问等 OpenAI 兼容）/ TestBrain(RAG) / offline 降级
- 文档解析（txt/md/docx/pdf）、Confluence 拉取
- 结构化 Prompt（含移动端场景提示）、P0 冒烟～P3
- 生成后完整性软检查（缺前置/步骤/预期等提示）
- 可选 `POST /ai-cases/import` 单向落库
- 知识库：同步当前 PRD、列表代理（依赖独立 TestBrain）

## TestBrain + Milvus（独立部署）

与主平台 compose **隔离**，目录：`deploy/testbrain/`。

```powershell
cd deploy/testbrain
# .env 已填 QWEN_API_KEY 时可跳过复制
# 推荐 Podman Desktop（替代 Docker Desktop）：
podman compose up -d --build
# 或：.\start-podman.ps1
# Docker Desktop：docker compose up -d --build
curl http://127.0.0.1:8001/api/v1/health
```

## 平台内嵌入（稳妥方式）

**不合并进程**：TestBrain 仍独立运行；ATP 仅做产品层嵌入。

| 入口 | 路径 | 说明 |
|------|------|------|
| TestBrain | `/testbrain` | 侧栏一点即嵌原生首页（iframe 同域反代） |
| 用例生成 | `/ai-cases` | 平台自有页 |
| 知识库 | `/ai-knowledge` | 平台页 + 契约 API |

开发：Vite 将 `/testbrain` → `http://10.0.98.20:8000`（可用环境变量 `TESTBRAIN_URL` 覆盖）。生产网关需配置同等反代。

ATP 对接：

| 配置/环境变量 | 说明 |
|---------------|------|
| `TESTBRAIN_URL` | 默认 `http://10.0.98.20:8000`（远程原版 TestBrain） |
| `ATP_AI_CASE_PROVIDER` | 联通后可设为 `testbrain`；本地默认仍可用 `llm` |
| 失败降级 | TestBrain 不可用时自动降级 LLM（有 Key）或 offline |

契约 API（overlay）：

- `GET /api/v1/health`
- `POST /api/v1/ai/generate-cases`
- `POST /api/v1/ai/knowledge/ingest`
- `GET /api/v1/ai/knowledge/list`

## 配置（ATP）

| 配置 | 说明 |
|------|------|
| `atp.ai-case.enabled` | 总开关 |
| `atp.ai-case.provider` | `llm` / `testbrain` / `offline` |
| `ATP_LLM_*` | 大模型 |
| `TESTBRAIN_URL` | TestBrain 根地址 |
| `CONFLUENCE_*` | 可选 |

## API

- `GET /api/v1/ai-cases/status` → 含 `scope.testbrain_deployed` / `scope.rag_knowledge_base`
- `POST /api/v1/ai-cases/generate` → `{ drafts, quality, review_summary, provider_used }`
- `POST /api/v1/ai-cases/import`
- `POST /api/v1/ai-cases/parse-document`
- `POST /api/v1/ai-cases/fetch-confluence`
- `POST /api/v1/ai-cases/knowledge/ingest`
- `POST /api/v1/ai-cases/knowledge/ingest-prd`
- `GET /api/v1/ai-cases/knowledge/list`

## 关闭

```yaml
atp:
  ai-case:
    enabled: false
```
