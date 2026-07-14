# ATP 执行步骤录屏锚点规范（ATP_STEP_*）

> **版本**：v1.0  
> **目标 Sprint**：Sprint 4（P0-C4 执行失败一键回放）  
> **对齐文档**：`视频录制核心.md` §3.2.4、§5 验收标准第 3 条

---

## 1. 背景

录制审阅页已通过 `video_offset_ms` 实现步骤与视频双向联动，但**任务执行失败场景**缺少统一的时间锚点，无法在 TaskDetail 一键跳转到报错录屏片段。

本规范定义 executor 脚本运行时输出的**结构化日志标记**，由 backend 解析后写入 `execution_step_markers` 表，供 `GET /tasks/{id}/failure-playback` 使用。

---

## 2. 标记格式

### 2.1 步骤开始（必须）

```
ATP_STEP_BEGIN step={n} type={step_type} offset_ms={ms} [display={name}] [recording_id={id}]
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `step` | int | 是 | 0-based 步骤索引，与用例 steps 数组一致 |
| `type` | string | 是 | 步骤类型，如 click、input、swipe |
| `offset_ms` | long | 是 | 相对录屏开始的毫秒偏移；无录屏时为 0 |
| `display` | string | 否 | 业务描述，URL 编码或不含空格 |
| `recording_id` | long | 否 | 已知关联录屏 ID（通常由 backend 回填） |

**示例**

```
ATP_STEP_BEGIN step=0 type=launch offset_ms=0 display=启动应用
ATP_STEP_BEGIN step=3 type=click offset_ms=12480 display=点击登录
ATP_STEP_BEGIN step=7 type=input offset_ms=42300 display=输入手机号
```

### 2.2 步骤结束（推荐）

```
ATP_STEP_END step={n} status={ok|fail|skip} offset_ms={ms} [error={msg}]
```

| 字段 | 说明 |
|------|------|
| `status` | ok=成功；fail=失败；skip=跳过 |
| `error` | 失败时简短错误（≤200 字符，不含换行） |

**示例**

```
ATP_STEP_END step=3 status=ok offset_ms=13200
ATP_STEP_END step=7 status=fail offset_ms=45100 error=ElementNotFound:submit_btn
```

### 2.3 录屏关联（可选，任务级）

```
ATP_RECORDING_BEGIN recording_id={id} task_id={tid} execution_id={eid}
ATP_RECORDING_END recording_id={id} duration_ms={ms} file={path}
```

由 executor `main.py` 在 `_start_recording` / `_stop_recording` 时输出，便于 backend 绑定 task 与 recording_resources。

---

## 3. 输出位置与级别

| 输出渠道 | log_type | level | 说明 |
|----------|----------|-------|------|
| executor 脚本 stdout | script | info | VisualScriptGenerator 每步 emit |
| executor main.py | platform | info | 录屏启停 |
| 失败时 | script | error | STEP_END status=fail 可伴随 error 日志 |

**约束**

- 每条标记独占一行（或作为 log message 唯一内容）
- 不使用 JSON 包裹（与现有 `ATP_CRASH_LOG:` / `ATP_FAILURE_SNAPSHOT:` 风格一致）
- `display` / `error` 含空格时用 `_` 替代，或 backend 仅解析到第一个空格前

---

## 4. Executor 实现要点

### 4.1 VisualScriptGenerator（Java 生成脚本时注入）

在 `VisualScriptGenerator.java` 每个步骤执行前/后追加：

```python
# 步骤开始前（伪代码，由 generator 写入每步）
_step_idx = {index}
_step_type = "{type}"
_offset_ms = _recording_offset_ms()  # 见 4.2
print(f"ATP_STEP_BEGIN step={_step_idx} type={_step_type} offset_ms={_offset_ms} display={_display_safe}")

# 步骤 try/except 结束后
print(f"ATP_STEP_END step={_step_idx} status=ok offset_ms={_recording_offset_ms()}")
# except:
print(f"ATP_STEP_END step={_step_idx} status=fail offset_ms={_recording_offset_ms()} error={_err_safe}")
raise
```

`_display_safe`：取 `display_name` 或 `element_name`，替换空格为 `_`，截断 64 字符。

### 4.2 录屏偏移计算（executor 公共模块）

新建 `executor/step_marker_helper.py`：

```python
import os
import time

_RECORDING_T0: float | None = None

def mark_recording_start():
    global _RECORDING_T0
    _RECORDING_T0 = time.monotonic()

def recording_offset_ms() -> int:
    if _RECORDING_T0 is None:
        return 0
    return max(0, int((time.monotonic() - _RECORDING_T0) * 1000))

def emit_step_begin(step: int, step_type: str, display: str = ""):
    safe = (display or "").replace(" ", "_")[:64]
    extra = f" display={safe}" if safe else ""
    print(f"ATP_STEP_BEGIN step={step} type={step_type} offset_ms={recording_offset_ms()}{extra}")

def emit_step_end(step: int, status: str, error: str = ""):
    err = f" error={error.replace(' ', '_')[:200]}" if error else ""
    print(f"ATP_STEP_END step={step} status={status} offset_ms={recording_offset_ms()}{err}")
```

在 `main.py` 的 `_start_recording` 后调用 `mark_recording_start()`。

### 4.3 main.py 录屏标记

```python
# _start_recording 成功后
logs.append(LogEntry(..., message=f"ATP_RECORDING_BEGIN task_id={req.task_id} execution_id={req.execution_id}"))

# _stop_recording 成功后
logs.append(LogEntry(..., message=f"ATP_RECORDING_END duration_ms={duration_ms} file={recording_path}"))
```

---

## 5. Backend 解析与存储

### 5.1 正则

```java
Pattern STEP_BEGIN = Pattern.compile(
    "ATP_STEP_BEGIN step=(\\d+) type=(\\w+) offset_ms=(\\d+)(?: display=([^\\s]+))?");
Pattern STEP_END = Pattern.compile(
    "ATP_STEP_END step=(\\d+) status=(ok|fail|skip) offset_ms=(\\d+)(?: error=(.+))?");
Pattern RECORDING_BEGIN = Pattern.compile(
    "ATP_RECORDING_BEGIN(?: recording_id=(\\d+))? task_id=(\\d+) execution_id=(\\d+)");
```

### 5.2 ExecutionStepMarkerService（新建）

```java
public void ingestFromLogs(Long taskId, Long executionId, Long deviceId,
                           Long recordingId, List<String> messages) {
    for (String msg : messages) {
        Matcher m = STEP_BEGIN.matcher(msg);
        if (m.find()) {
            ExecutionStepMarker marker = new ExecutionStepMarker();
            marker.setTaskId(taskId);
            marker.setExecutionId(executionId);
            marker.setDeviceId(deviceId);
            marker.setRecordingId(recordingId);
            marker.setStepIndex(Integer.parseInt(m.group(1)));
            marker.setStepType(m.group(2));
            marker.setVideoOffsetMs(Long.parseLong(m.group(3)));
            marker.setDisplayName(m.group(4));
            marker.setLogLine(msg);
            repository.save(marker);
        }
        // STEP_END status=fail → 更新 failed_step_index 缓存
    }
}
```

**调用时机**：`SchedulerService` 任务完成后 ingest logs（与现有 `FailureSnapshotService.ingestFromLogs` 并列）。

### 5.3 failure-playback API

```java
@GetMapping("/api/v1/tasks/{taskId}/failure-playback")
public ApiResponse<Map<String, Object>> failurePlayback(@PathVariable Long taskId) {
    // 1. 找 task 最近 failed 的 execution
    // 2. 找 STEP_END status=fail 或 task.failed_step_index
    // 3. 找同 task 的 recording_resources（task_id 或 execution 关联）
    // 4. 找 execution_step_markers where step_index = failed
    return Map.of(
        "task_id", taskId,
        "failed_step_index", 7,
        "video_offset_ms", 42300,
        "recording_id", 15,
        "playback_url", "/api/v1/recordings/15/stream",
        "step_display_name", "点击提交订单",
        "failure_message", "ElementNotFound:submit_btn"
    );
}
```

---

## 6. 前端消费

### TaskDetail.vue

```javascript
async function replayFailureSegment() {
  const res = await taskApi.failurePlayback(taskId)
  const d = res.data
  playbackUrl.value = await recordingApi.playbackStream(d.recording_id)
  showPlayer.value = true
  nextTick(() => playerRef.value?.seekToMs(d.video_offset_ms))
}
```

在「失败步骤 #N」旁增加按钮「回放失败片段」。

---

## 7. 与现有 ATP 标记的关系

| 标记 | 用途 | 服务 |
|------|------|------|
| `ATP_CRASH_LOG:` | 崩溃日志 | FailureSnapshotService |
| `ATP_FAILURE_SNAPSHOT:` | 失败现场 JSON | FailureSnapshotService |
| `ATP_PERF_METRICS:` | 性能指标 | DefectLinkService |
| `ATP_CONTROLS_JSON:` | 控件采集 | ControlPoolService |
| **`ATP_STEP_BEGIN/END`** | **步骤录屏锚点** | **ExecutionStepMarkerService（新）** |
| **`ATP_RECORDING_BEGIN/END`** | **任务录屏生命周期** | **RecordingService 扩展** |

---

## 8. 测试验证

| 用例 | 操作 | 预期 |
|------|------|------|
| SM-01 | 执行 10 步用例且 enable_recording | execution_logs 含 10 条 STEP_BEGIN |
| SM-02 | 第 5 步故意失败 | STEP_END status=fail；failure-playback offset 对应第 5 步 |
| SM-03 | TaskDetail 点击回放失败片段 | 播放器 seek 到正确时间点 |
| SM-04 | 无录屏任务 | failure-playback 返回 recording_id=null，提示无关联录屏 |

---

## 9. 兼容与 rollout

1. **Phase 1**：仅 VisualScriptGenerator 生成的新脚本带标记；旧脚本无标记时不报错
2. **Phase 2**：backend ingest 写入 `execution_step_markers`
3. **Phase 3**：TaskDetail / ReportDetail UI 接入
4. 旧日志无 `offset_ms` 时，fallback 按 step_index 比例估算：`offset = duration * step / totalSteps`

---

## 10. 变更清单

| 组件 | 文件 | 变更 |
|------|------|------|
| Executor | `executor/step_marker_helper.py` | 新建 |
| Executor | `executor/main.py` | 录屏起止标记 |
| Backend | `VisualScriptGenerator.java` | 每步 emit BEGIN/END |
| Backend | `ExecutionStepMarkerService.java` | 新建 |
| Backend | `ExecutionStepMarkerController.java` | failure-playback API |
| Backend | `SchedulerService.java` | 完成后 ingest markers |
| Frontend | `TaskDetail.vue` | 回放失败片段按钮 |
| Frontend | `api/index.js` | taskApi.failurePlayback |
| DB | `005_recording_p0_enhance.sql` | execution_step_markers 表 |
