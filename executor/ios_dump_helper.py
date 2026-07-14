"""iOS WDA page source dump + 坐标拾取（M4-01）"""

from __future__ import annotations

import json
import re
import urllib.request
import xml.etree.ElementTree as ET

from ios_helper import wda_base
from locator_recommend import build_recommended_chain, pick_primary_from_locators, build_nspredicate


def wda_source(agent_host: str, wda_port: int = 8100) -> str:
    url = wda_base(agent_host, wda_port) + "/source"
    with urllib.request.urlopen(url, timeout=20) as resp:
        body = resp.read().decode("utf-8", errors="ignore")
        if body.strip().startswith("{"):
            data = json.loads(body)
            return str(data.get("value") or data.get("source") or "")
        return body


def _parse_ios_rect(node: ET.Element) -> tuple[int, int, int, int] | None:
    bounds = node.get("bounds") or ""
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4))
    try:
        x = int(float(node.get("x") or node.get("X") or 0))
        y = int(float(node.get("y") or node.get("Y") or 0))
        w = int(float(node.get("width") or node.get("W") or 0))
        h = int(float(node.get("height") or node.get("H") or 0))
        if w > 0 and h > 0:
            return x, y, x + w, y + h
    except ValueError:
        pass
    return None


def _contains_point(rect: tuple[int, int, int, int], x: int, y: int) -> bool:
    return rect[0] <= x <= rect[2] and rect[1] <= y <= rect[3]


def _ios_node_area(rect: tuple[int, int, int, int]) -> int:
    return max(0, rect[2] - rect[0]) * max(0, rect[3] - rect[1])


def find_ios_node_at_point(source_xml: str, x: int, y: int) -> ET.Element | None:
    if not source_xml:
        return None
    try:
        root = ET.fromstring(source_xml)
    except ET.ParseError:
        return None
    best: ET.Element | None = None
    best_area = 10**12
    for node in root.iter():
        rect = _parse_ios_rect(node)
        if not rect or not _contains_point(rect, x, y):
            continue
        area = _ios_node_area(rect)
        if area < best_area:
            best_area = area
            best = node
    return best


def _ios_element_name(node: ET.Element) -> str:
    for key in ("name", "label", "value", "identifier", "resource-id"):
        val = (node.get(key) or "").strip()
        if val:
            return re.sub(r"\W+", "_", val.lower())[:48]
    cls = (node.get("type") or node.get("class") or "element").split(".")[-1]
    return re.sub(r"\W+", "_", cls.lower())[:48] or "ios_element"


def _ios_build_locators(node: ET.Element) -> dict[str, str]:
    locs: dict[str, str] = {}
    identifier = (node.get("name") or node.get("identifier") or "").strip()
    label = (node.get("label") or node.get("value") or "").strip()
    cls = (node.get("type") or node.get("class") or "").strip()
    if identifier:
        locs["id"] = identifier
        locs["accessibility_id"] = identifier
    if label:
        locs["text"] = label
        locs["content_desc"] = label
    if cls:
        short = cls.split(".")[-1]
        locs["class_name"] = short
    nsp = build_nspredicate(node)
    if nsp:
        locs["nspredicate"] = nsp
    bounds = node.get("bounds") or ""
    if not bounds:
        rect = _parse_ios_rect(node)
        if rect:
            bounds = f"[{rect[0]},{rect[1]}][{rect[2]},{rect[3]}]"
    if bounds:
        locs["bounds"] = bounds
    return locs


def inspect_ios_point(agent_host: str, wda_port: int, x: int, y: int) -> dict:
    result: dict = {
        "x": int(x),
        "y": int(y),
        "platform": "ios",
        "context": "native",
        "source": "ios_wda",
        "element_name": "",
        "display_name": "",
        "locator_type": "",
        "locator_value": "",
        "locators": {},
        "valid": False,
        "widget_type": "unknown",
        "suggested_step_type": "click",
    }
    try:
        source = wda_source(agent_host, wda_port)
    except Exception as exc:
        result["inspect_error"] = f"wda_source_failed:{exc}"
        return result
    node = find_ios_node_at_point(source, x, y)
    if node is None:
        result["inspect_error"] = "no_node_at_point"
        return result
    locs = _ios_build_locators(node)
    loc_type, loc_val = pick_primary_from_locators(locs, "ios", None)
    chain = build_recommended_chain(locs, "ios", None)
    rec = next((c for c in chain if c.get("recommended")), None)
    label = (node.get("label") or node.get("value") or node.get("name") or "").strip()
    identifier = (node.get("name") or node.get("identifier") or "").strip()
    result.update({
        "valid": bool(loc_val or locs),
        "element_name": _ios_element_name(node),
        "display_name": label or identifier or result["element_name"],
        "locator_type": loc_type or ("id" if identifier else "content_desc"),
        "locator_value": loc_val or identifier or label or locs.get("bounds", ""),
        "locators": locs,
        "locator_chain": chain,
        "text": label,
        "class": node.get("type") or node.get("class") or "",
        "bounds": locs.get("bounds", ""),
    })
    if rec:
        result["recommend_reason"] = rec.get("recommend_reason", "")
        result["recommended_locator_type"] = rec.get("type")
        result["recommended_locator_value"] = rec.get("value")
    return result
