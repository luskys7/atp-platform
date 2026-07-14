"""设备系统语言切换（Android）"""

import subprocess
from typing import Optional

LOCALE_MAP = {
    "zh_cn": "zh-CN",
    "zh_tw": "zh-TW",
    "en_us": "en-US",
    "ja_jp": "ja-JP",
    "ko_kr": "ko-KR",
}


def normalize_locale(code: str) -> str:
    key = (code or "en_us").lower().replace("-", "_")
    return LOCALE_MAP.get(key, code.replace("_", "-"))


def set_device_locale(serial: str, locale_code: str) -> str:
    if not serial:
        return "skip: no serial"
    locale = normalize_locale(locale_code)
    parts = locale.split("-")
    lang = parts[0] if parts else "en"
    country = parts[1] if len(parts) > 1 else "US"
    cmds = [
        ["adb", "-s", serial, "shell", "settings", "put", "system", "system_locales", locale],
        ["adb", "-s", serial, "shell", "setprop", "persist.sys.locale", locale],
        ["adb", "-s", serial, "shell", "setprop", "persist.sys.language", lang],
        ["adb", "-s", serial, "shell", "setprop", "persist.sys.country", country],
    ]
    for cmd in cmds:
        try:
            subprocess.run(cmd, capture_output=True, text=True, timeout=6)
        except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
            pass
    return f"locale set to {locale}"


def get_device_locale(serial: str) -> str:
    if not serial:
        return ""
    try:
        r = subprocess.run(
            ["adb", "-s", serial, "shell", "getprop", "persist.sys.locale"],
            capture_output=True, text=True, timeout=5,
        )
        return (r.stdout or "").strip()
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return ""
