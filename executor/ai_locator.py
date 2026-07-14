"""AI 元素定位 - 基于 UI 层级 dump 的启发式匹配（本地可运行，无需外网 API）"""

from adb_client import adb_dump_ui, adb_shell
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path


def locate(serial: str, platform: str, query: str, app_package: str = "") -> dict:
    if platform == "android":
        return _locate_android(serial, query, app_package)
    raise ValueError(f"平台 {platform} 暂不支持 AI 定位，请使用 Android 设备")


def dump_ui(serial: str) -> str:
    """返回当前界面 UI dump 文本，供断言使用"""
    import tempfile
    from pathlib import Path

    remote = "/sdcard/atp_ui_dump.xml"
    local = Path(tempfile.gettempdir()) / f"atp_ui_{serial}.xml"
    if not adb_dump_ui(serial, str(local), remote=remote, timeout=8):
        return ""
    return local.read_text(encoding="utf-8", errors="ignore")


def _locate_android(serial: str, query: str, app_package: str) -> dict:
    remote = "/sdcard/atp_ui_dump.xml"
    local = Path(tempfile.gettempdir()) / f"atp_ui_{serial}.xml"
    if not adb_dump_ui(serial, str(local), remote=remote, timeout=30):
        raise RuntimeError("uiautomator dump 失败")

    tree = ET.parse(local)
    query_lower = query.lower().strip()
    candidates = []

    for node in tree.iter("node"):
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        rid = (node.get("resource-id") or "").strip()
        clazz = (node.get("class") or "").strip()
        bounds = node.get("bounds") or ""
        clickable = node.get("clickable") == "true"

        if app_package and rid and app_package not in rid:
            continue

        score = _score(query_lower, text, desc, rid)
        if score <= 0:
            continue

        locator_type, locator_value = _to_locator(text, desc, rid, bounds)
        candidates.append({
            "score": score + (0.1 if clickable else 0),
            "locator_type": locator_type,
            "locator_value": locator_value,
            "bounds": bounds,
            "class": clazz,
        })

    if not candidates:
        raise ValueError(f"未找到匹配 '{query}' 的控件")

    best = max(candidates, key=lambda c: c["score"])
    confidence = min(0.99, best["score"])
    return {
        "success": True,
        "locator_type": best["locator_type"],
        "locator_value": best["locator_value"],
        "confidence": round(confidence, 3),
        "strategy": "uiautomator_text_match",
        "bounds": best["bounds"],
    }


def _score(query: str, text: str, desc: str, rid: str) -> float:
    text_l = text.lower()
    desc_l = desc.lower()
    rid_l = rid.lower()
    if query == text_l or query == desc_l:
        return 0.95
    if query in text_l or query in desc_l:
        return 0.85
    if query in rid_l:
        return 0.75
    # 分词模糊
    for part in re.split(r"[\s_\-]+", query):
        if len(part) >= 2 and (part in text_l or part in desc_l or part in rid_l):
            return 0.65
    return 0.0


def _to_locator(text: str, desc: str, rid: str, bounds: str) -> tuple[str, str]:
    if rid:
        if ":id/" in rid:
            rid_short = rid.split(":id/")[-1]
            return "id", rid_short
        return "id", rid
    if text:
        escaped = text.replace('"', '\\"')
        return "xpath", f'//*[@text="{escaped}"]'
    if desc:
        escaped = desc.replace('"', '\\"')
        return "xpath", f'//*[@content-desc="{escaped}"]'
    if bounds:
        return "bounds", bounds
    return "xpath", "//*"
