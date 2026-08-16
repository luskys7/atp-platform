# 便携种子（控件池 + 用例）

本目录由 `scripts/export-portable-seed.ps1` 从当前 H2 库导出，供：

1. **Git 协作 / 换机**：提交本目录（含 `atp_portable_seed.zip`）
2. **空库自动导入**：同名 zip 同步到 `backend-java/src/main/resources/seed/`
3. **手动还原**：拷到 `backend-java/data/seed/` 或平台「灾备备份」中还原

## 换机步骤

```powershell
# 在有数据的机器上导出
powershell -ExecutionPolicy Bypass -File .\scripts\export-portable-seed.ps1

# 提交或拷贝 fixtures/portable-seed/atp_portable_seed.zip
# 新机器：放到 backend-java/data/seed/ 后启动本地后端即可自动导入（空库时）
```

或在平台配置 → 灾备备份 → 还原 `atp_portable_seed.zip`。
