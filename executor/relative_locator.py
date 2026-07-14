"""相对定位 — 父容器+下标 / 锚点邻位 / 区域限定（M2）"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET

from record_helper import (
    _class_short,
    _find_parent,
    _is_dynamic,
    _is_dynamic_text,
    _is_generic_rid,
    _is_inside_dialog,
    _is_layout_container,
    _locator_exists_on_node,
    _node_is_tappable,
    _node_text_matches,
    _parse_bounds,
)

_LIST_CONTAINERS = ("recyclerview", "listview", "gridview", "viewpager", "scrollview", "viewgroup")


def parse_locator_kv(value: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for part in str(value or "").split("|"):
        part = part.strip()
        if not part or "=" not in part:
            continue
        k, v = part.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def format_locator_kv(**kwargs: str) -> str:
    return "|".join(f"{k}={v}" for k, v in kwargs.items() if v is not None and str(v) != "")


def _bounds_intersect(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    return not (a[2] <= b[0] or a[0] >= b[2] or a[3] <= b[1] or a[1] >= b[3])


def _node_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    return _parse_bounds(node.get("bounds") or "")


def _node_center(bounds: tuple[int, int, int, int]) -> tuple[int, int]:
    return (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2


def _root_bounds(root: ET.Element) -> tuple[int, int, int, int] | None:
    for node in root.iter("node"):
        parsed = _node_bounds(node)
        if parsed:
            return parsed
    return None


def node_is_effectively_visible(node: ET.Element, root: ET.Element | None = None) -> bool:
    if (node.get("displayed") or "").lower() == "false":
        return False
    if (node.get("enabled") or "true").lower() == "false":
        return False
    parsed = _node_bounds(node)
    if not parsed:
        return False
    x1, y1, x2, y2 = parsed
    if x2 <= x1 or y2 <= y1:
        return False
    if root is not None:
        screen = _root_bounds(root)
        if screen and not _bounds_intersect(parsed, screen):
            return False
    return True


def _child_index_in_container(container: ET.Element, target: ET.Element) -> int:
    children = list(container)
    if target in children:
        return children.index(target)
    for idx, child in enumerate(children):
        for sub in child.iter():
            if sub is target:
                return idx
    return -1


def build_parent_index(node: ET.Element, root: ET.Element) -> str:
    current: ET.Element | None = node
    container: ET.Element | None = None
    for _ in range(10):
        parent = _find_parent(root, current) if current is not None else None
        if parent is None:
            break
        cls = _class_short(parent.get("class") or "").lower()
        child_count = len(list(parent))
        if any(c in cls for c in _LIST_CONTAINERS) or child_count >= 3:
            container = parent
            break
        current = parent
    if container is None:
        return ""
    rid = (container.get("resource-id") or "").strip()
    if not rid or _is_generic_rid(rid):
        return ""
    idx = _child_index_in_container(container, node)
    if idx < 0:
        return ""
    short = rid.split(":id/")[-1] if ":id/" in rid else rid
    return format_locator_kv(container=short, index=str(idx))


def build_anchor_adjacent(node: ET.Element, root: ET.Element) -> str:
    parent = _find_parent(root, node)
    if parent is None:
        return ""
    siblings = list(parent)
    try:
        idx = siblings.index(node)
    except ValueError:
        return ""
    for i in range(idx - 1, -1, -1):
        label = (siblings[i].get("text") or siblings[i].get("content-desc") or "").strip()
        if label and not _is_dynamic_text(label):
            return format_locator_kv(anchor=label, dir="right")
    for i in range(idx + 1, len(siblings)):
        label = (siblings[i].get("text") or siblings[i].get("content-desc") or "").strip()
        if label and not _is_dynamic_text(label):
            return format_locator_kv(anchor=label, dir="left")
    return ""


def _find_region_container(node: ET.Element, root: ET.Element) -> ET.Element | None:
    if _is_inside_dialog(node, root):
        current: ET.Element | None = node
        for _ in range(8):
            if current is None:
                break
            if "dialog" in _class_short(current.get("class") or "").lower():
                return current
            current = _find_parent(root, current)
    current = node
    screen = _root_bounds(root)
    for _ in range(6):
        parent = _find_parent(root, current)
        if parent is None or parent is root:
            break
        pb = _node_bounds(parent)
        if screen and pb:
            area = (pb[2] - pb[0]) * (pb[3] - pb[1])
            sa = (screen[2] - screen[0]) * (screen[3] - screen[1])
            if sa > 0 and area / sa < 0.72 and not _is_layout_container(parent.get("class") or ""):
                return parent
        current = parent
    return None


def build_region_locator(node: ET.Element, root: ET.Element, text: str, desc: str, rid: str) -> str:
    region_node = _find_region_container(node, root)
    if region_node is None:
        return ""
    region_bounds = region_node.get("bounds") or ""
    if not region_bounds:
        return ""
    inner_type = ""
    inner_value = ""
    if desc and not _is_dynamic_text(desc):
        inner_type, inner_value = "content_desc", desc
    elif rid and not _is_generic_rid(rid):
        inner_type, inner_value = "id", rid.split(":id/")[-1] if ":id/" in rid else rid
    elif text and not _is_dynamic_text(text):
        inner_type, inner_value = "text", text
    else:
        return ""
    return format_locator_kv(region=region_bounds, type=inner_type, value=inner_value)


def _find_anchor_node(root: ET.Element, anchor: str) -> ET.Element | None:
    for node in root.iter("node"):
        if _node_text_matches(node, anchor):
            return node
    return None


def _pick_directional_target(anchor_node: ET.Element, root: ET.Element, direction: str) -> ET.Element | None:
    parent = _find_parent(root, anchor_node)
    if parent is None:
        return None
    siblings = list(parent)
    try:
        idx = siblings.index(anchor_node)
    except ValueError:
        return None
    candidates: list[ET.Element] = []
    if direction == "right":
        candidates = siblings[idx + 1:]
    elif direction == "left":
        candidates = list(reversed(siblings[:idx]))
    elif direction == "down":
        ab = _node_bounds(anchor_node)
        if ab:
            ax, ay = _node_center(ab)
            for node in root.iter("node"):
                if node is anchor_node:
                    continue
                nb = _node_bounds(node)
                if not nb:
                    continue
                cx, cy = _node_center(nb)
                if cy > ay and abs(cx - ax) < (ab[2] - ab[0]):
                    candidates.append(node)
    elif direction == "up":
        ab = _node_bounds(anchor_node)
        if ab:
            ax, ay = _node_center(ab)
            for node in root.iter("node"):
                if node is anchor_node:
                    continue
                nb = _node_bounds(node)
                if not nb:
                    continue
                cx, cy = _node_center(nb)
                if cy < ay and abs(cx - ax) < (ab[2] - ab[0]):
                    candidates.append(node)
    for cand in candidates:
        if _node_is_tappable(cand) and node_is_effectively_visible(cand, root):
            return cand
        for sub in cand.iter():
            if sub is not cand and _node_is_tappable(sub) and node_is_effectively_visible(sub, root):
                return sub
    return None


def _resolve_parent_index(root: ET.Element, value: str) -> list[ET.Element]:
    kv = parse_locator_kv(value)
    container_key = kv.get("container", "")
    try:
        index = int(kv.get("index", -1))
    except ValueError:
        return []
    if not container_key or index < 0:
        return []
    for node in root.iter("node"):
        rid = (node.get("resource-id") or "").strip()
        short = rid.split(":id/")[-1] if ":id/" in rid else rid
        if not rid or not (short == container_key or rid.endswith("/" + container_key) or container_key in rid):
            continue
        children = list(node)
        if index >= len(children):
            continue
        target = children[index]
        if _node_is_tappable(target) and node_is_effectively_visible(target, root):
            return [target]
        for sub in target.iter():
            if _node_is_tappable(sub) and node_is_effectively_visible(sub, root):
                return [sub]
        return [target]
    return []


def _resolve_anchor_adjacent(root: ET.Element, value: str) -> list[ET.Element]:
    kv = parse_locator_kv(value)
    anchor = kv.get("anchor", "")
    direction = kv.get("dir", "right").lower()
    if not anchor:
        return []
    anchor_node = _find_anchor_node(root, anchor)
    if anchor_node is None:
        return []
    target = _pick_directional_target(anchor_node, root, direction)
    return [target] if target is not None else []


def _resolve_region_locator(root: ET.Element, value: str) -> list[ET.Element]:
    kv = parse_locator_kv(value)
    region = kv.get("region", "")
    inner_type = kv.get("type", "content_desc")
    inner_value = kv.get("value", "")
    region_parsed = _parse_bounds(region)
    if not region_parsed or not inner_value:
        return []
    matched: list[ET.Element] = []
    for node in root.iter("node"):
        nb = _node_bounds(node)
        if not nb or not _bounds_intersect(nb, region_parsed):
            continue
        if not node_is_effectively_visible(node, root):
            continue
        if _locator_exists_on_node(node, inner_type, inner_value):
            matched.append(node)
    tappable = [n for n in matched if _node_is_tappable(n)]
    return tappable or matched


def find_nodes_for_relative_locator(root: ET.Element, key: str, value: str) -> list[ET.Element]:
    if key == "parent_index":
        return _resolve_parent_index(root, value)
    if key == "anchor_adjacent":
        return _resolve_anchor_adjacent(root, value)
    if key == "region_locator":
        return _resolve_region_locator(root, value)
    return []


def build_relative_locators(node: ET.Element, root: ET.Element, text: str, desc: str, rid: str) -> dict[str, str]:
    out: dict[str, str] = {}
    pi = build_parent_index(node, root)
    if pi:
        out["parent_index"] = pi
    aa = build_anchor_adjacent(node, root)
    if aa:
        out["anchor_adjacent"] = aa
    rl = build_region_locator(node, root, text, desc, rid)
    if rl:
        out["region_locator"] = rl
    return out
