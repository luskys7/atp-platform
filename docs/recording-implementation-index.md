# 视频录制核心改造 · 设计文档索引

本目录包含 `视频录制核心.md` 需求的对照实施文档。

| 文档 | 说明 |
|------|------|
| [recording-atp-step-marker-spec.md](./recording-atp-step-marker-spec.md) | Executor `ATP_STEP_BEGIN/END` 日志打点规范；Sprint 4 失败回放前后端对齐 |
| [recording-widget-type-inference.md](./recording-widget-type-inference.md) | `record_helper.py` widget_type 推断补丁（Spinner/Dialog/Upload 等） |
| [recording-testcases-p0-p2.csv](./recording-testcases-p0-p2.csv) | **70 条**测试用例（UTF-8 CSV，可 Excel/禅道导入） |

## 已落地代码（2026-07-12 续）

| 模块 | 文件 | 状态 |
|------|------|------|
| 一键录制 last_device | `QuickRecordFab.vue` / `DeviceScreen.vue` | ✅ 记住上次设备，FAB 直达 |
| 静默极速启动 | `DeviceScreen.vue` `maybeAutoStartRecording` | ✅ auto_record 默认标签静默开录 |
| 录制步骤不注入执行辅助 | `OperationRecordService.finalizeVisualSteps` | ✅ dismiss/check 仅脚本生成期注入 |
| 导航栏不写入步骤 | `DeviceScreen.vue` `pressNavKey` | ✅ 避免误触 Home 产生伪步骤 |
| select/confirm/upload 脚本 | `VisualScriptGenerator.java` | ✅ 支持三类业务步骤执行 |
| 需人工补定位标记 | `OperationRecordService` + `RecordCaseReview.vue` | ✅ `needs_manual_fix` |
| 校验元素展示 | `RecordCaseReview.vue` | ✅ `validation_elements` |
| 步骤拆分 | `RecordCaseReview.vue` | ✅ 插入等待步骤拆分 |
| 30s 草稿 autosave | `DeviceScreen.vue` + `useVideoRecorder.getPartialBlob` | ✅ IndexedDB 周期缓存 |
| 关页提醒 | `DeviceScreen.vue` `beforeunload` | ✅ 录制中防误关 |
| 生产强制脱敏 | `DeviceScreen.vue` + `OperationRecordService` | ✅ 项目编码含 prod/生产 时锁定 |
| CPU 自适应降帧 | `useVideoRecorder.js` | ✅ 6–12fps 动态调节 + 状态栏展示 |
| 启动耗时埋点 | `useRecordingStartup.js` + meta.startup_ms | ✅ FAB→开录计时，审阅页展示 |
| 录制性能指标 | upload client_metrics_json | ✅ record_fps_avg / paint_ms_avg / performance_grade |
| 窗口模式裁剪 | `useVideoRecorder.js` + `DeviceScreen.vue` | ✅ full/window/crop 三模式 |
| 继续录制卡片 | `Dashboard.vue` | ✅ 上次设备 + 进行中会话 |
| 识别率批量审计 | `GET /operation-records/audit-summary` | ✅ Dashboard + 录制质量页 |
| recording_v2 灰度开关 | `AtpProperties.recording` + `/recording/features` | ✅ 关闭时隐藏 FAB/状态栏 |
| 定位二次校验 | `POST /operation-records/{id}/validate-locators` | ✅ 审阅页抽检 |
| 录制质量报告页 | `RecordingQuality.vue` | ✅ 性能分布 + 会话明细 |
| upload OCR 兜底 | `record_helper._ocr_upload_fallback` | ✅ 上传文案识别 |
| 导航键可选录制 | `DeviceScreen` recordNavKeys | ✅ Back/多任务/菜单，不含 Home |
| 关联任务 ID | 录制 meta.task_id | ✅ 归档标签 + 审阅页跳转 |
| hover/rich_text 脚本 | `VisualScriptGenerator` + record_helper | ✅ 独立步骤类型 |
| 录屏配置页 | `PlatformConfig` 录屏配置 Tab | ✅ 可编辑保存/重置，运行时立即生效 |
| 运行时录屏配置 | `RecordingConfigService` + `global_parameters` | ✅ v2 开关与阈值无需重启 |
| CI 批量 API 审计 | `.github/workflows/recording-p0.yml` | ✅ Secrets 可选启用 |
| upload adb push + 文件选择器 | `file_upload_helper.py` + `VisualScriptGenerator` | ✅ push + GET_CONTENT |
| 画面标注持久化 | `RecordingPlayer.vue` + `RecordCaseReview.vue` | ✅ video_annotations 入库 |
| upload adb push | `VisualScriptGenerator.java` | ✅ 本地文件 push 到设备 |
| 录屏列表步骤 markers | `RecordingService.getPlaybackContext` + `Recordings.vue` | ✅ |
| 标注编辑/删除 | `RecordingPlayer.vue` | ✅ 列表编辑标签、删除、跳转 |
| 录制实时定位警告 | `DeviceScreen.vue` liveRecentSteps | ✅ 未识别黄标 |
| P0 验收门禁 | `RecordingQuality.vue` + `audit-summary` p0_acceptance | ✅ 识别率/启动/CPU 三项门禁 |
| 水印 hash 核验 | `GET /recordings/{id}/watermark-verify` + Recordings | ✅ payload 与 hash 对比 |
| 订单号脱敏 | `DesensitizeUtil` 对齐前端规则 | ✅ 后端步骤日志覆盖 |
| P0 验收脚本 | `scripts/verify-recording-p0.py` | ✅ 12 项含脱敏 + P0 门禁 |
| 提交回归 | `RecordCaseReview.vue` + `caseApi.run` | ✅ 一键转用例并调度 |
| 步骤锚点 helper | `executor/step_marker_helper.py` | ✅ 已实现 |
| widget_type 推断 | `executor/record_helper.py` | ✅ 已实现 |

## CI 批量审计（P0-C1）

在 GitHub 仓库 **Settings → Secrets and variables → Actions** 中配置：

| Secret | 说明 |
|--------|------|
| `ATP_API_BASE` | 平台 API 根地址，如 `https://atp.example.com/api/v1` |
| `ATP_TOKEN` | 具备读取 `operation-records/audit-summary` 的 Bearer Token |

配置后 workflow 会在 P0 脚本通过后额外执行批量审计。本地等效命令：

```bash
ATP_BATCH_AUDIT=1 ATP_API_BASE=https://... ATP_TOKEN=... python scripts/verify-recording-p0.py
```

## 待完善（下一迭代）

（当前 Sprint 7 录屏核心项已全部落地，后续按业务反馈迭代）

## Sprint 用例分布

| Sprint | 条数 | 范围 |
|--------|------|------|
| Sprint 1 | 10 | 选区/异常/一键启动/标签/切点 |
| Sprint 2 | 10 | 检索/水印/播放器 |
| Sprint 3 | 14 | 定位/识别/双视图/指标 |
| Sprint 4 | 13 | 联动/编辑/分段/失败回放/性能/ATP标记 |
| Sprint 5 | 8 | 脱敏/兜底/命中率 |
| Sprint 6 | 5 | 自动化转换 |
| Sprint 7 | 10 | 调度/灰度/全量验收 |
