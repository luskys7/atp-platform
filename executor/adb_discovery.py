"""USB 真机自动发现：扫描 adb devices 并采集基础信息"""

import re
import subprocess
from typing import Any


def _run(cmd: list[str], timeout: int = 15) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, check=False)


def _device_props(serial: str) -> dict[str, Any]:
    info: dict[str, Any] = {"serial_number": serial, "platform": "android"}
    try:
        info["os_version"] = _run(
            ["adb", "-s", serial, "shell", "getprop", "ro.build.version.release"], timeout=8
        ).stdout.strip()
        info["model"] = _run(
            ["adb", "-s", serial, "shell", "getprop", "ro.product.model"], timeout=8
        ).stdout.strip()
        size_out = _run(["adb", "-s", serial, "shell", "wm", "size"], timeout=8).stdout
        m = re.search(r"(\d+)x(\d+)", size_out or "")
        if m:
            info["screen_width"] = int(m.group(1))
            info["screen_height"] = int(m.group(2))
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        pass
    name = info.get("model") or serial
    info["name"] = name
    info["battery_level"] = 100
    info["adb_port"] = 5037
    info["agent_host"] = "127.0.0.1"
    info["agent_port"] = 9100
    return info


def list_usb_devices() -> dict[str, Any]:
    """返回当前 USB 已连接且处于 device 状态的 Android 真机列表。"""
    proc = _run(["adb", "devices", "-l"], timeout=10)
    if proc.returncode != 0:
        return {"success": False, "message": proc.stderr or proc.stdout or "adb 执行失败", "devices": []}

    devices: list[dict[str, Any]] = []
    for line in (proc.stdout or "").splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        if state != "device":
            continue
        model = ""
        for token in parts[2:]:
            if token.startswith("model:"):
                model = token.split(":", 1)[1]
        info = _device_props(serial)
        if model and not info.get("model"):
            info["model"] = model
            info["name"] = model
        devices.append(info)

    return {"success": True, "count": len(devices), "devices": devices}
