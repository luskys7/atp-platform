"""Android UiSelector 生成与解析（M4-09）"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET


def build_uiselector(node: ET.Element, text: str, desc: str, rid: str) -> str:
    parts = ["new UiSelector()"]
    if rid and rid.strip():
        short = rid.split(":id/")[-1] if ":id/" in rid else rid.strip()
        if short:
            parts.append(f'.resourceId("{short}")')
    elif text and text.strip():
        parts.append(f'.text("{text.replace(chr(34), "")}")')
    elif desc and desc.strip():
        parts.append(f'.description("{desc.replace(chr(34), "")}")')
    cls = (node.get("class") or "").split(".")[-1]
    if len(parts) == 1 and cls:
        parts.append(f'.className("{cls}")')
    if node.get("clickable") == "true":
        parts.append(".clickable(true)")
    return "".join(parts) if len(parts) > 1 else ""


def parse_uiselector(expr: str) -> dict[str, str]:
    out: dict[str, str] = {}
    s = str(expr or "")
    for key, pattern in (
        ("resource_id", r'resourceId\("([^"]+)"\)'),
        ("text", r'text\("([^"]+)"\)'),
        ("content_desc", r'description\("([^"]+)"\)'),
        ("class_name", r'className\("([^"]+)"\)'),
    ):
        m = re.search(pattern, s)
        if m:
            out[key] = m.group(1)
    return out


def find_nodes_for_uiselector(root: ET.Element, expr: str) -> list[ET.Element]:
    from record_helper import _locator_exists_on_node, _node_is_visible

    kv = parse_uiselector(expr)
    if not kv:
        return []
    matched: list[ET.Element] = []
    for node in root.iter("node"):
        ok = True
        for key, val in kv.items():
            mapped = "content_desc" if key == "content_desc" else key
            if not _locator_exists_on_node(node, mapped, val):
                ok = False
                break
        if ok and _node_is_visible(node, root):
            if "clickable" in expr and node.get("clickable") != "true":
                continue
            matched.append(node)
    return matched
