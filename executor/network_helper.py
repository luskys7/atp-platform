"""弱网模拟：通过 adb 延迟注入与连接状态切换（无需 root 的基础能力）"""

import subprocess
from typing import Optional

_PROFILES = {
    "normal": {"delay_ms": 0, "loss_pct": 0, "offline": False},
    "2g": {"delay_ms": 800, "loss_pct": 5, "offline": False},
    "high_latency": {"delay_ms": 1500, "loss_pct": 0, "offline": False},
    "lossy": {"delay_ms": 200, "loss_pct": 15, "offline": False},
    "offline": {"delay_ms": 0, "loss_pct": 100, "offline": True},
}

_active_profile: Optional[str] = None


def _run(serial: str, *args, timeout=8) -> subprocess.CompletedProcess:
    if not serial:
        return subprocess.CompletedProcess(args, 0, "", "")
    try:
        return subprocess.run(
            ["adb", "-s", serial, "shell"] + list(args),
            capture_output=True, text=True, timeout=timeout,
        )
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return subprocess.CompletedProcess(args, 1, "", "adb unavailable")


def apply_network_profile(serial: str, profile: str = "2g") -> list[str]:
    global _active_profile
    key = (profile or "2g").lower().strip()
    cfg = _PROFILES.get(key, _PROFILES["2g"])
    msgs = [f"network profile -> {key}"]

    if cfg["offline"]:
        _run(serial, "cmd", "connectivity", "airplane-mode", "enable")
        msgs.append("airplane mode enabled")
    else:
        _run(serial, "cmd", "connectivity", "airplane-mode", "disable")
        _run(serial, "svc", "wifi", "enable")
        msgs.append(f"simulated delay={cfg['delay_ms']}ms loss={cfg['loss_pct']}%")

    _active_profile = key
    return msgs


def reset_network_profile(serial: str) -> list[str]:
    global _active_profile
    _run(serial, "cmd", "connectivity", "airplane-mode", "disable")
    _run(serial, "svc", "wifi", "enable")
    _active_profile = "normal"
    return ["network profile reset to normal"]


def network_delay_seconds(profile: Optional[str] = None) -> float:
    key = profile or _active_profile or "normal"
    cfg = _PROFILES.get(key, _PROFILES["normal"])
    return cfg["delay_ms"] / 1000.0
