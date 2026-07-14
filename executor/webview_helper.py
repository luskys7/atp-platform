"""WebView / 原生上下文检测与切换（基于 UIAutomator 混合应用支持）"""

from __future__ import annotations

import os
import re
import subprocess
import xml.etree.ElementTree as ET

from ai_locator import dump_ui

_CONTEXT = "native"
_WEBVIEW_CLASS_MARKERS = ("android.webkit.webview", "com.android.webview", "chromium", "webview")


def current_context() -> str:
    return os.environ.get("ATP_UI_CONTEXT", _CONTEXT)


def _is_webview_class(clazz: str) -> bool:
    lower = (clazz or "").lower()
    return any(m in lower for m in _WEBVIEW_CLASS_MARKERS)


def _parse_bounds(bounds: str) -> tuple[int, int, int, int] | None:
    nums = [int(n) for n in re.findall(r"\d+", bounds or "")]
    if len(nums) >= 4:
        return nums[0], nums[1], nums[2], nums[3]
    return None


def _contains_point(bounds: str, x: int, y: int) -> bool:
    rect = _parse_bounds(bounds)
    if not rect:
        return False
    return rect[0] <= x <= rect[2] and rect[1] <= y <= rect[3]


def _find_parent(root: ET.Element, node: ET.Element) -> ET.Element | None:
    for parent in root.iter():
        if node in list(parent):
            return parent
    return None


def find_node_at_point(root: ET.Element, x: int, y: int) -> ET.Element | None:
    best: ET.Element | None = None
    best_area = 10**12
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        if not _contains_point(bounds, x, y):
            continue
        rect = _parse_bounds(bounds)
        if not rect:
            continue
        area = max(1, (rect[2] - rect[0]) * (rect[3] - rect[1]))
        if area < best_area:
            best_area = area
            best = node
    return best


def node_in_webview_context(root: ET.Element, node: ET.Element | None) -> bool:
    current = node
    depth = 0
    while current is not None and depth < 12:
        if _is_webview_class(current.get("class") or ""):
            return True
        current = _find_parent(root, current)
        depth += 1
    return False


def detect_context_from_xml(xml: str) -> str:
    if not xml:
        return "native"
    lower = xml.lower()
    if "android.webkit.webview" in lower or "com.android.webview" in lower:
        return "webview"
    if "webview" in lower and ('clickable="true"' in lower or 'focusable="true"' in lower):
        return "hybrid"
    return "native"


def detect_context_at_point(root: ET.Element | None, x: int, y: int) -> str:
    if root is None:
        return "native"
    node = find_node_at_point(root, x, y)
    if node is not None and (_is_webview_class(node.get("class") or "") or node_in_webview_context(root, node)):
        return "webview"
    page = detect_context_from_xml(ET.tostring(root, encoding="unicode"))
    return page


def should_auto_switch_context(page_context: str, point_context: str, pick: dict | None = None) -> bool:
    if current_context() == "webview":
        return False
    if point_context in ("webview", "hybrid"):
        return True
    if page_context in ("webview", "hybrid"):
        return True
    if pick:
        clazz = str(pick.get("class") or "")
        if _is_webview_class(clazz):
            return True
        tags = pick.get("risk_tags") or []
        if "webview" in tags:
            return True
    return False


def auto_switch_for_inspect(
    serial: str,
    root: ET.Element | None,
    x: int,
    y: int,
    page_context: str,
    pick: dict | None = None,
) -> tuple[bool, list[str]]:
    point_context = detect_context_at_point(root, x, y) if root is not None else page_context
    if not should_auto_switch_context(page_context, point_context, pick):
        return False, []
    logs = switch_context(serial, "webview")
    logs.insert(0, f"auto-switch: page={page_context}, point={point_context}")
    return True, logs


def detect_context(serial: str) -> str:
    xml = dump_ui(serial)
    return detect_context_from_xml(xml)


def switch_context(serial: str, target: str = "auto") -> list[str]:
    """切换 UI 上下文：native / webview / auto"""
    logs: list[str] = []
    global _CONTEXT

    if target == "auto":
        detected = detect_context(serial)
        target = "webview" if detected in ("webview", "hybrid") else "native"
        logs.append(f"auto-detect -> {detected}, switch to {target}")

    if target == "webview":
        os.environ["ATP_UI_CONTEXT"] = "webview"
        _CONTEXT = "webview"
        logs.append("Context switched to WEBVIEW (UIAutomator hybrid mode)")
    elif target == "native":
        os.environ["ATP_UI_CONTEXT"] = "native"
        _CONTEXT = "native"
        subprocess.run(
            ["adb", "-s", serial, "shell", "input", "keyevent", "KEYCODE_BACK"],
            capture_output=True, text=True, timeout=5,
        )
        logs.append("Context switched to NATIVE")
    else:
        raise ValueError(f"Unknown context target: {target}")
    try:
        from record_helper import invalidate_ui_cache
        invalidate_ui_cache(serial)
        logs.append("UI cache invalidated after context switch")
    except Exception:
        pass
    return logs
