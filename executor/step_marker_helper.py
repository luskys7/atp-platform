"""任务执行步骤与录屏时间轴锚点（ATP_STEP_BEGIN/END）。"""

from __future__ import annotations

import os
import re
import time

_RECORDING_T0: float | None = None

_STEP_BEGIN_RE = re.compile(
    r"ATP_STEP_BEGIN step=(\d+) type=(\w+) offset_ms=(\d+)(?: display=([^\s]+))?"
)
_STEP_END_RE = re.compile(
    r"ATP_STEP_END step=(\d+) status=(ok|fail|skip|interrupt|exception|ignore) offset_ms=(\d+)(?: error=(.+))?"
)
# 兼容旧版 VisualScriptGenerator 输出
_LEGACY_STEP_START_RE = re.compile(r"ATP_STEP_START:step=(\d+):type=(\w+)")
_LEGACY_STEP_OK_RE = re.compile(r"ATP_STEP_OK:step=(\d+)")
_LEGACY_CHECKPOINT_FAILED_RE = re.compile(r"CHECKPOINT_FAILED:step=(\d+)")


def mark_recording_start() -> None:
    global _RECORDING_T0
    _RECORDING_T0 = time.monotonic()


def reset_recording_clock() -> None:
    global _RECORDING_T0
    _RECORDING_T0 = None


def recording_offset_ms() -> int:
    env_started = os.environ.get("ATP_RECORDING_STARTED_AT")
    if env_started:
        try:
            return max(0, int((time.time() - float(env_started)) * 1000))
        except ValueError:
            pass
    if _RECORDING_T0 is None:
        return 0
    return max(0, int((time.monotonic() - _RECORDING_T0) * 1000))


def _safe_token(text: str, max_len: int = 64) -> str:
    return (text or "").replace(" ", "_").replace("\n", "_")[:max_len]


def emit_step_begin(step: int, step_type: str, display: str = "") -> None:
    safe = _safe_token(display)
    extra = f" display={safe}" if safe else ""
    print(f"ATP_STEP_BEGIN step={step} type={step_type} offset_ms={recording_offset_ms()}{extra}")


def emit_step_end(step: int, status: str = "ok", error: str = "") -> None:
    err = f" error={_safe_token(error, 200)}" if error else ""
    print(f"ATP_STEP_END step={step} status={status} offset_ms={recording_offset_ms()}{err}")


def emit_recording_begin(task_id: int, execution_id: int, recording_id: int | None = None) -> str:
    rid = f" recording_id={recording_id}" if recording_id else ""
    msg = f"ATP_RECORDING_BEGIN task_id={task_id} execution_id={execution_id}{rid}"
    print(msg)
    return msg


def emit_recording_end(duration_ms: int, file_path: str = "", recording_id: int | None = None) -> str:
    rid = f" recording_id={recording_id}" if recording_id else ""
    fp = _safe_token(file_path, 256)
    extra = f" file={fp}" if fp else ""
    msg = f"ATP_RECORDING_END duration_ms={duration_ms}{rid}{extra}"
    print(msg)
    return msg
