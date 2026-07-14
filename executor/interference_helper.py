"""系统干扰降噪：勿扰模式 + 通知屏蔽"""

import subprocess
from typing import List


def _run(serial: str, *args, timeout=6) -> subprocess.CompletedProcess:
    if not serial:
        return subprocess.CompletedProcess(args, 0, "", "")
    try:
        return subprocess.run(
            ["adb", "-s", serial, "shell"] + list(args),
            capture_output=True, text=True, timeout=timeout,
        )
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return subprocess.CompletedProcess(args, 1, "", "adb error")


def enable_focus_mode(serial: str) -> List[str]:
    logs: List[str] = []
    cmds = [
        (["settings", "put", "global", "zen_mode", "1"], "zen_mode=1"),
        (["settings", "put", "global", "heads_up_notifications_enabled", "0"], "heads_up off"),
        (["cmd", "notification", "set_interruption_filter", "2"], "interruption_filter=none"),
    ]
    for args, label in cmds:
        _run(serial, *args)
        logs.append(f"focus: {label}")
    return logs


def disable_focus_mode(serial: str) -> List[str]:
    logs: List[str] = []
    _run(serial, "settings", "put", "global", "zen_mode", "0")
    _run(serial, "settings", "put", "global", "heads_up_notifications_enabled", "1")
    _run(serial, "cmd", "notification", "set_interruption_filter", "1")
    logs.append("focus: restored")
    return logs
