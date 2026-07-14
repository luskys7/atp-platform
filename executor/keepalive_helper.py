"""后台保活：屏幕常亮"""

import subprocess
from typing import List


def _run(serial: str, *args, timeout=6):
    if not serial:
        return
    try:
        subprocess.run(
            ["adb", "-s", serial, "shell"] + list(args),
            capture_output=True, text=True, timeout=timeout,
        )
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        pass


def enable_keep_awake(serial: str) -> List[str]:
    _run(serial, "svc", "power", "stayon", "true")
    _run(serial, "settings", "put", "system", "screen_off_timeout", "2147483647")
    return ["keep_awake: screen stay on"]


def disable_keep_awake(serial: str) -> List[str]:
    _run(serial, "svc", "power", "stayon", "false")
    _run(serial, "settings", "put", "system", "screen_off_timeout", "60000")
    return ["keep_awake: restored"]
