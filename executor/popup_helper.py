"""系统弹窗自动拦截：权限、协议、广告等常见按钮"""

import re
import subprocess
import xml.etree.ElementTree as ET


POPUP_TEXTS = ["允许", "同意", "确定", "我知道了", "稍后", "跳过", "关闭", "Accept", "OK", "Allow"]


def dismiss_android_popups(serial: str, max_rounds: int = 3) -> list[str]:
    logs: list[str] = []
    for _ in range(max_rounds):
        xml = _dump_ui(serial)
        if not xml:
            break
        clicked = False
        for text in POPUP_TEXTS:
            bounds = _find_bounds_by_text(xml, text)
            if bounds:
                x, y = _center(bounds)
                subprocess.run(
                    ["adb", "-s", serial, "shell", "input", "tap", str(x), str(y)],
                    capture_output=True, timeout=10,
                )
                logs.append(f"dismiss tap: {text} @ ({x},{y})")
                clicked = True
                break
        if not clicked:
            break
    return logs


def _dump_ui(serial: str) -> str:
    import tempfile
    from pathlib import Path
    from adb_client import adb_dump_ui
    remote = "/sdcard/atp_ui_dump.xml"
    local = Path(tempfile.gettempdir()) / f"atp_popup_{serial}.xml"
    if not adb_dump_ui(serial, str(local), remote=remote, timeout=8):
        return ""
    return local.read_text(encoding="utf-8", errors="ignore")


def _find_bounds_by_text(xml: str, text: str) -> str:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return ""
    for node in root.iter("node"):
        attrs = node.attrib
        label = attrs.get("text", "") or attrs.get("content-desc", "")
        if text in label and attrs.get("clickable") == "true":
            b = attrs.get("bounds", "")
            if b:
                return b
    return ""


def _center(bounds: str) -> tuple[int, int]:
    nums = [int(n) for n in re.findall(r"\d+", bounds)]
    if len(nums) >= 4:
        return (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2
    return 540, 960
