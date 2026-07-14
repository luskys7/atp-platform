"""性能指标采集：启动耗时、内存"""

import json
import subprocess
import time
from typing import Optional


def collect_memory(serial: str, app_package: Optional[str]) -> dict:
    if not serial or not app_package:
        return {"error": "missing serial or package"}
    try:
        r = subprocess.run(
            ["adb", "-s", serial, "shell", "dumpsys", "meminfo", app_package],
            capture_output=True, text=True, timeout=8,
        )
        text = r.stdout or ""
        total_pss = 0
        for line in text.splitlines():
            if "TOTAL PSS" in line.upper() or "TOTAL:" in line.upper():
                nums = [int(x.replace(",", "")) for x in line.split() if x.replace(",", "").isdigit()]
                if nums:
                    total_pss = nums[0]
                    break
        return {"package": app_package, "total_pss_kb": total_pss, "raw_len": len(text)}
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError) as e:
        return {"error": str(e)}


def measure_cold_start(serial: str, app_package: str, activity: str = "") -> dict:
    if not serial or not app_package:
        return {"error": "missing serial or package"}
    try:
        subprocess.run(
            ["adb", "-s", serial, "shell", "am", "force-stop", app_package],
            capture_output=True, text=True, timeout=5,
        )
        time.sleep(0.5)
        component = activity if activity else app_package
        t0 = time.time()
        subprocess.run(
            ["adb", "-s", serial, "shell", "monkey", "-p", app_package, "-c", "android.intent.category.LAUNCHER", "1"],
            capture_output=True, text=True, timeout=15,
        )
        elapsed_ms = int((time.time() - t0) * 1000)
        mem = collect_memory(serial, app_package)
        return {
            "cold_start_ms": elapsed_ms,
            "package": app_package,
            "component": component,
            "memory": mem,
        }
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError) as e:
        return {"error": str(e)}


def emit_perf_marker(data: dict) -> str:
    return "ATP_PERF_METRICS:" + json.dumps(data, ensure_ascii=False)
