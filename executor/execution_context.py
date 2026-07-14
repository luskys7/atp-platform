"""执行上下文隔离、权限授予、坐标校准"""

import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Optional


DEFAULT_CALIBRATION = {"offset_x": 0, "offset_y": 0, "scale_x": 1.0, "scale_y": 1.0}

ANDROID_PERMISSIONS = [
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_PHONE_STATE",
    "android.permission.POST_NOTIFICATIONS",
]


def calibrate_point(x: int, y: int, calibration: Optional[dict] = None) -> tuple[int, int]:
    cal = calibration or DEFAULT_CALIBRATION
    ox = float(cal.get("offset_x", 0) or 0)
    oy = float(cal.get("offset_y", 0) or 0)
    sx = float(cal.get("scale_x", 1) or 1)
    sy = float(cal.get("scale_y", 1) or 1)
    return int(x * sx + ox), int(y * sy + oy)


def parse_calibration_json(raw: Optional[str]) -> dict:
    if not raw:
        return dict(DEFAULT_CALIBRATION)
    try:
        data = json.loads(raw)
        return {**DEFAULT_CALIBRATION, **data}
    except json.JSONDecodeError:
        return dict(DEFAULT_CALIBRATION)


class ExecutionContext:
    """单次执行隔离目录，执行结束自动清理"""

    def __init__(self, task_id: int, execution_id: int, enabled: bool = True):
        self.enabled = enabled
        self.work_dir: Optional[Path] = None
        if enabled:
            base = Path(tempfile.gettempdir()) / "atp_exec"
            base.mkdir(parents=True, exist_ok=True)
            self.work_dir = Path(tempfile.mkdtemp(prefix=f"task{task_id}_exec{execution_id}_", dir=base))

    def env_overlay(self) -> dict[str, str]:
        if not self.enabled or not self.work_dir:
            return {}
        return {
            "ATP_EXEC_DIR": str(self.work_dir),
            "ATP_EXEC_ISOLATION": "1",
            "TMPDIR": str(self.work_dir),
            "TEMP": str(self.work_dir),
        }

    def cleanup(self):
        if self.work_dir and self.work_dir.exists():
            shutil.rmtree(self.work_dir, ignore_errors=True)


def grant_android_permissions(serial: str, app_package: Optional[str]) -> list[str]:
    logs: list[str] = []
    if not app_package:
        return logs
    for perm in ANDROID_PERMISSIONS:
        cmd = ["adb", "-s", serial, "shell", "pm", "grant", app_package, perm]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        if result.returncode == 0:
            logs.append(f"granted {perm}")
        elif "Unknown permission" not in (result.stderr or ""):
            logs.append(f"grant skip {perm}: {(result.stderr or result.stdout or '').strip()[:80]}")
    return logs


def revoke_android_permissions(serial: str, app_package: Optional[str]) -> list[str]:
    """执行结束后回收运行时权限（pm revoke）"""
    logs: list[str] = []
    if not app_package:
        return logs
    for perm in ANDROID_PERMISSIONS:
        cmd = ["adb", "-s", serial, "shell", "pm", "revoke", app_package, perm]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        out = (result.stderr or result.stdout or "").strip()
        if result.returncode == 0:
            logs.append(f"revoked {perm}")
        elif "Unknown permission" in out or "not granted" in out.lower():
            logs.append(f"revoke skip {perm}")
        else:
            logs.append(f"revoke skip {perm}: {out[:80]}")
    return logs


def clear_app_cache(serial: str, app_package: Optional[str], mode: str = "disk") -> list[str]:
    """精细化缓存清理：disk=磁盘缓存, memory=杀进程, all=两者"""
    logs: list[str] = []
    if not app_package:
        logs.append("clear_app_cache: no package")
        return logs
    if mode in ("memory", "all"):
        cmd = ["adb", "-s", serial, "shell", "am", "force-stop", app_package]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        logs.append(f"force-stop {app_package}: rc={result.returncode}")
    if mode in ("disk", "all"):
        cmd = ["adb", "-s", serial, "shell", "pm", "clear", "--cache-only", app_package]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            fallback = ["adb", "-s", serial, "shell", "cmd", "package", "trim-caches", app_package, "999G"]
            result = subprocess.run(fallback, capture_output=True, text=True, timeout=30)
        logs.append(f"clear cache ({mode}) {app_package}: rc={result.returncode}")
    return logs
