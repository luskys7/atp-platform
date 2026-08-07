"""AI 元素定位 - 基于 UI 层级 dump 的启发式匹配（本地可运行，无需外网 API）"""

from adb_client import adb_shell
import re
import xml.etree.ElementTree as ET


def locate(serial: str, platform: str, query: str, app_package: str = "") -> dict:
    if platform == "android":
        return _locate_android(serial, query, app_package)
    raise ValueError(f"平台 {platform} 暂不支持 AI 定位，请使用 Android 设备")


def dump_ui(serial: str) -> str:
    """返回当前界面 UI dump 文本（优先 uiautomator2 hierarchy）。"""
    from ui_dump_helper import dump_ui_text
    return dump_ui_text(serial, prefer_u2=True, force=False)


def _locate_android(serial: str, query: str, app_package: str) -> dict:
    from ui_dump_helper import dump_ui_xml

    res = dump_ui_xml(serial, prefer_u2=True, force=True, timeout=30)
    if not res.get("ok"):
        raise RuntimeError(res.get("error") or "UI dump 失败")

    tree = ET.parse(res["local_path"])
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
        "strategy": "u2_hierarchy_text_match",
        "dump_source": res.get("source"),
        "bounds": best["bounds"],
    }


def _score(query: str, text: str, desc: str, rid: str) -> float:
    text_l = text.lower()
    desc_l = desc.lower()
    rid_l = rid.lower()
    if query == text_l or query == desc_l:
        return 1.0
    if query in text_l or query in desc_l:
        return 0.85
    if query in rid_l:
        return 0.7
    tokens = [t for t in re.split(r"[\s_\-./:]+", query) if len(t) >= 2]
    hit = sum(1 for t in tokens if t in text_l or t in desc_l or t in rid_l)
    if hit:
        return 0.4 + 0.15 * min(hit, 3)
    return 0.0


def _to_locator(text: str, desc: str, rid: str, bounds: str) -> tuple[str, str]:
    if rid:
        return "id", rid
    if desc:
        return "content_desc", desc
    if text:
        return "text", text
    return "bounds", bounds
