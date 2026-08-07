"""将 UI dump XML 转为前端可浏览的轻量 JSON 树。"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from typing import Any


MAX_DEPTH = 28
MAX_NODES = 2500
MAX_CHILDREN_SHOWN = 120
MAX_LABEL_LEN = 48


def _short_class(clazz: str) -> str:
    return (clazz or "").split(".")[-1] or "node"


def _clip(val: str, n: int = MAX_LABEL_LEN) -> str:
    s = str(val or "").strip()
    if len(s) <= n:
        return s
    return s[: n - 1] + "…"


def _node_label(attrs: dict[str, Any]) -> str:
    short = attrs.get("class") or "node"
    tip = attrs.get("contentDesc") or attrs.get("text") or attrs.get("resourceId") or ""
    if tip:
        return f"{short} · {_clip(str(tip), 36)}"
    return short


def _parse_bounds_center(bounds: str) -> tuple[int | None, int | None]:
    import re
    nums = [int(n) for n in re.findall(r"\d+", bounds or "")]
    if len(nums) < 4:
        return None, None
    x1, y1, x2, y2 = nums[:4]
    return (x1 + x2) // 2, (y1 + y2) // 2


def element_to_tree_node(
    el: ET.Element,
    *,
    counter: list[int],
    depth: int = 0,
    stats: dict[str, int],
) -> dict[str, Any] | None:
    if el.tag not in ("node", "hierarchy") and not str(el.tag).endswith("node"):
        # 只导出 hierarchy / node
        if depth == 0 and str(el.tag) == "hierarchy":
            pass
        elif str(el.tag) != "node":
            return None

    if stats["nodes"] >= MAX_NODES:
        stats["truncated"] = 1
        return None

    counter[0] += 1
    nid = f"n{counter[0]}"
    stats["nodes"] += 1

    clazz_full = (el.get("class") or "").strip()
    text = (el.get("text") or "").strip()
    desc = (el.get("content-desc") or "").strip()
    rid = (el.get("resource-id") or "").strip()
    bounds = (el.get("bounds") or "").strip()
    package = (el.get("package") or "").strip()
    index_raw = el.get("index")
    try:
        index = int(index_raw) if index_raw is not None and str(index_raw).strip() != "" else None
    except ValueError:
        index = None

    cx, cy = _parse_bounds_center(bounds)
    attrs: dict[str, Any] = {
        "id": nid,
        "class": _short_class(clazz_full),
        "classFull": clazz_full or None,
        "text": text or None,
        "contentDesc": desc or None,
        "resourceId": rid or None,
        "package": package or None,
        "bounds": bounds or None,
        "clickable": el.get("clickable") == "true",
        "enabled": el.get("enabled") != "false",
        "index": index,
        "centerX": cx,
        "centerY": cy,
    }
    attrs["label"] = _node_label(attrs)

    children_out: list[dict[str, Any]] = []
    raw_children = [c for c in list(el) if isinstance(c.tag, str)]
    if depth >= MAX_DEPTH:
        if raw_children:
            attrs["truncated"] = True
            stats["truncated"] = 1
        attrs["children"] = []
        return attrs

    shown = 0
    for child in raw_children:
        if stats["nodes"] >= MAX_NODES:
            attrs["truncated"] = True
            stats["truncated"] = 1
            break
        if shown >= MAX_CHILDREN_SHOWN:
            attrs["truncated"] = True
            stats["truncated"] = 1
            break
        if child.tag != "node" and str(child.tag) != "node":
            # hierarchy 下也可能套 node
            if str(child.tag) != "hierarchy":
                # still try if it looks like a UI node (has class/bounds)
                if child.get("class") is None and child.get("bounds") is None:
                    continue
        node = element_to_tree_node(child, counter=counter, depth=depth + 1, stats=stats)
        if node:
            children_out.append(node)
            shown += 1

    attrs["children"] = children_out
    return attrs


def xml_to_hierarchy_tree(xml: str) -> dict[str, Any]:
    """返回 { ok, root, nodeCount, truncated, dumpBytes }。"""
    if not xml or not str(xml).strip():
        return {"ok": False, "error": "empty_xml", "root": None, "nodeCount": 0}
    try:
        root_el = ET.fromstring(xml)
    except ET.ParseError as e:
        return {"ok": False, "error": f"xml_parse_failed:{e}", "root": None, "nodeCount": 0}

    counter = [0]
    stats = {"nodes": 0, "truncated": 0}

    # hierarchy 根：导出其子树；若根就是 node 则直接导出
    if str(root_el.tag) == "hierarchy":
        children = []
        for child in list(root_el):
            node = element_to_tree_node(child, counter=counter, depth=0, stats=stats)
            if node:
                children.append(node)
        root_node = {
            "id": "root",
            "class": "hierarchy",
            "label": "hierarchy",
            "children": children,
            "clickable": False,
        }
    else:
        root_node = element_to_tree_node(root_el, counter=counter, depth=0, stats=stats)

    return {
        "ok": True,
        "root": root_node,
        "nodeCount": stats["nodes"],
        "truncated": bool(stats["truncated"]),
        "dumpBytes": len(xml.encode("utf-8", errors="ignore")),
    }


def find_node_by_id(root: dict | None, node_id: str) -> dict | None:
    if not root or not node_id:
        return None
    if root.get("id") == node_id:
        return root
    for ch in root.get("children") or []:
        hit = find_node_by_id(ch, node_id)
        if hit:
            return hit
    return None


def find_node_by_bounds(root: dict | None, bounds: str) -> dict | None:
    if not root or not bounds:
        return None
    b = str(bounds).strip()
    if root.get("bounds") == b:
        return root
    for ch in root.get("children") or []:
        hit = find_node_by_bounds(ch, b)
        if hit:
            return hit
    return None
