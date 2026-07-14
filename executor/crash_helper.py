"""应用崩溃/ANR 日志采集"""

import re
import subprocess
from typing import Optional


def capture_crash_logs(serial: str, app_package: Optional[str] = None, tail: int = 300) -> dict:
    result = {"fatal_lines": [], "anr_lines": [], "raw_tail": ""}
    if not serial:
        result["error"] = "no device serial"
        return result
    try:
        proc = subprocess.run(
            ["adb", "-s", serial, "logcat", "-d", "-t", str(tail)],
            capture_output=True, text=True, timeout=8,
        )
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError) as e:
        result["error"] = str(e)
        return result
    text = proc.stdout or ""
    result["raw_tail"] = text[-8000:]

    pkg = (app_package or "").strip()
    for line in text.splitlines():
        upper = line.upper()
        if "FATAL EXCEPTION" in upper or "ANDROIDRUNTIME" in upper:
            if not pkg or pkg in line:
                result["fatal_lines"].append(line[:500])
        if "ANR" in upper or "NOT RESPONDING" in upper:
            if not pkg or pkg in line:
                result["anr_lines"].append(line[:500])

    if app_package:
        try:
            ps = subprocess.run(
                ["adb", "-s", serial, "shell", "pidof", app_package],
                capture_output=True, text=True, timeout=5,
            )
            result["process_alive"] = bool((ps.stdout or "").strip())
        except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
            result["process_alive"] = None
    else:
        result["process_alive"] = None

    try:
        dropbox = subprocess.run(
            ["adb", "-s", serial, "shell", "dumpsys", "dropbox", "--print"],
            capture_output=True, text=True, timeout=8,
        )
        if dropbox.stdout:
            blocks = re.split(r"\n={5,}\n", dropbox.stdout)
            for block in blocks[-3:]:
                if pkg and pkg not in block:
                    continue
                if "crash" in block.lower() or "anr" in block.lower():
                    result.setdefault("dropbox_snippets", []).append(block[:2000])
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        pass

    return result
