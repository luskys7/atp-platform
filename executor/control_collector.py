"""执行时自动采集当前页面可交互控件，供后端入库控件池"""

import re
import xml.etree.ElementTree as ET

from ai_locator import dump_ui


def _parse_bounds(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def _element_name(text: str, desc: str, rid: str, clazz: str, index: int) -> str:
    for candidate in (text, desc):
        if candidate and len(candidate.strip()) >= 1:
            safe = re.sub(r"[^\w\u4e00-\u9fff]", "_", candidate.strip())[:48]
            if safe:
                return safe
    if rid:
        short = rid.split("/")[-1].replace(":", "_")
        if short:
            return short[:48]
    short_cls = (clazz or "node").split(".")[-1]
    return f"{short_cls}_{index}"


def _to_locator(text: str, desc: str, rid: str, bounds: str) -> tuple[str, str]:
    if rid:
        val = rid.split("/")[-1] if "/" in rid else rid
        return "id", val
    if text:
        return "text", text
    if desc:
        return "desc", desc
    center = _parse_bounds(bounds)
    if center:
        return "xy", f"{center[0]},{center[1]}"
    return "xpath", "//node"


def collect_page_controls(serial: str, app_package: str = "", max_items: int = 40) -> list[dict]:
    """从 UI dump 采集可点击/可编辑控件，去重后返回入库结构"""
    xml = dump_ui(serial)
    if not xml:
        return []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []

    seen: set[str] = set()
    items: list[dict] = []
    page_name = "auto_page"

    for idx, node in enumerate(root.iter("node")):
        clickable = node.get("clickable") == "true"
        focusable = node.get("focusable") == "true"
        enabled = node.get("enabled") != "false"
        if not enabled or not (clickable or focusable):
            continue

        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        rid = (node.get("resource-id") or "").strip()
        clazz = (node.get("class") or "").strip()
        bounds = node.get("bounds") or ""

        if app_package and rid and app_package not in rid:
            continue
        if not text and not desc and not rid:
            continue

        element_name = _element_name(text, desc, rid, clazz, idx)
        if element_name in seen:
            continue
        seen.add(element_name)

        locator_type, locator_value = _to_locator(text, desc, rid, bounds)
        items.append({
            "element_name": element_name,
            "locator_type": locator_type,
            "locator_value": locator_value,
            "page_name": page_name,
            "platform": "android",
        })
        if len(items) >= max_items:
            break
    return items
