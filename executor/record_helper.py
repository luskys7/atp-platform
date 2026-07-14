"""录制步骤增强：坐标反查控件、多维度定位采集、WebView 上下文识别、OCR 兜底"""

import os
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

from adb_client import adb_shell, try_adb_dump_ui, tap_priority_active, interaction_guard_blocks_dump
from ai_locator import dump_ui
from webview_helper import detect_context_from_xml, auto_switch_for_inspect
from locator_recommend import build_recommended_chain, pick_primary_from_locators, build_nspredicate

_DYNAMIC_ATTR_PATTERNS = (
    re.compile(r"^android:id/\w+_\d+$"),
    re.compile(r"^\d+$"),
    re.compile(r"^[a-f0-9]{8,}$", re.I),
    re.compile(r"^temp_|^random_|^uuid_", re.I),
)

_WIDGET_CLASS_RULES: list[tuple[tuple[str, ...], str]] = [
    (("spinner", "dropdown", "appcompatspinner"), "select"),
    (("alertdialog", "dialog", "popupwindow"), "confirm_dialog"),
    (("edittext", "autocompletetextview"), "input"),
    (("upload", "filepicker", "document"), "upload"),
    (("checkbox", "checkedtextview"), "checkbox"),
    (("switch", "toggle", "switchcompat"), "switch"),
]

_CONFIRM_TEXTS = frozenset({"确定", "确认", "ok", "yes", "同意", "允许", "继续", "取消"})

_INPUT_TYPE_TEXT = 0x00000001
_INPUT_TYPE_MULTILINE = 0x00020000

_ui_dump_cache: dict[str, tuple[float, str]] = {}
_ui_dump_lock = threading.Lock()
_refresh_inflight: set[str] = set()
_last_refresh_at: dict[str, float] = {}
CACHE_TTL_FRESH = 4.0
CACHE_TTL_STALE = 25.0
MIN_REFRESH_INTERVAL = 5.0


def _parse_bounds(bounds: str) -> tuple[int, int, int, int] | None:
    nums = [int(n) for n in re.findall(r"\d+", bounds or "")]
    if len(nums) >= 4:
        return nums[0], nums[1], nums[2], nums[3]
    return None


def _point_in_bounds(x: int, y: int, bounds: str) -> bool:
    parsed = _parse_bounds(bounds)
    if not parsed:
        return False
    x1, y1, x2, y2 = parsed
    return x1 <= x <= x2 and y1 <= y <= y2


def _bounds_area(bounds: str) -> int:
    parsed = _parse_bounds(bounds)
    if not parsed:
        return 10**12
    x1, y1, x2, y2 = parsed
    return max(1, (x2 - x1) * (y2 - y1))


def _is_dynamic(value: str) -> bool:
    if not value:
        return True
    for pat in _DYNAMIC_ATTR_PATTERNS:
        if pat.search(value):
            return True
    return False


_GENERIC_RID_SUFFIXES = frozenset({
    "content",
    "decor_content_parent",
    "action_bar_root",
    "navigationbarbackground",
    "statusbarbackground",
    "title_container",
    "content_parent",
    "nav_bar",
    "navigation_bar",
})

_LAYOUT_CONTAINER_CLASSES = frozenset({
    "framelayout",
    "linearlayout",
    "relativelayout",
    "viewgroup",
    "view",
    "drawerlayout",
    "coordinatorlayout",
    "constraintlayout",
})


def _rid_short(rid: str) -> str:
    if not rid:
        return ""
    return (rid.split(":id/")[-1] if ":id/" in rid else rid.split("/")[-1]).lower()


def _is_generic_rid(rid: str) -> bool:
    short = _rid_short(rid)
    if not short:
        return False
    if short in _GENERIC_RID_SUFFIXES:
        return True
    if short.endswith("_content") and len(short) <= 12:
        return True
    return False


def _is_layout_container(clazz: str) -> bool:
    return _class_short(clazz) in _LAYOUT_CONTAINER_CLASSES


def _node_area_ratio(node: ET.Element, screen_w: int, screen_h: int) -> float:
    parsed = _parse_bounds(node.get("bounds") or "")
    if not parsed or screen_w <= 0 or screen_h <= 0:
        return 1.0
    x1, y1, x2, y2 = parsed
    return max(0.0, min(1.0, ((x2 - x1) * (y2 - y1)) / (screen_w * screen_h)))


def _element_name(text: str, desc: str, rid: str, clazz: str = "") -> str:
    if text.strip():
        return re.sub(r"\W+", "_", text.strip())[:48].strip("_") or "text_element"
    if desc.strip():
        return re.sub(r"\W+", "_", desc.strip())[:48].strip("_") or "desc_element"
    if rid:
        short = rid.split(":id/")[-1] if ":id/" in rid else rid.split("/")[-1]
        cleaned = re.sub(r"\W+", "_", short)[:48].strip("_")
        if cleaned and not _is_dynamic(cleaned):
            return cleaned or "id_element"
    short_cls = _class_short(clazz)
    if short_cls and short_cls not in ("view", "framelayout", "linearlayout", "relativelayout", "viewgroup"):
        return f"{short_cls}_element"
    return "ui_element"


def _display_name(text: str, desc: str, rid: str) -> str:
    if text.strip():
        return text.strip()[:64]
    if desc.strip():
        return desc.strip()[:64]
    if rid:
        return rid.split(":id/")[-1] if ":id/" in rid else rid.split("/")[-1]
    return "未知控件"


def _locator_from_node(text: str, desc: str, rid: str) -> tuple[str, str]:
    if rid and not _is_generic_rid(rid) and not _is_dynamic(rid.split(":id/")[-1] if ":id/" in rid else rid):
        if ":id/" in rid:
            return "id", rid.split(":id/")[-1]
        return "id", rid
    if text and not _is_dynamic(text):
        escaped = text.replace('"', '\\"')
        return "xpath", f'//*[@text="{escaped}"]'
    if desc and not _is_dynamic(desc):
        escaped = desc.replace('"', '\\"')
        return "xpath", f'//*[@content-desc="{escaped}"]'
    return "bounds", ""


def _class_short(clazz: str) -> str:
    return (clazz or "").split(".")[-1].lower()


def _find_parent(root: ET.Element, target: ET.Element) -> ET.Element | None:
    for parent in root.iter():
        for child in list(parent):
            if child is target:
                return parent
    return None


def _is_rich_text(node: ET.Element) -> bool:
    raw = node.get("input-type") or node.get("inputType") or ""
    try:
        value = int(raw, 0)
        return bool(value & _INPUT_TYPE_TEXT) and bool(value & _INPUT_TYPE_MULTILINE)
    except ValueError:
        lowered = raw.lower()
        return "multiline" in lowered or "textmultiline" in lowered


def _is_inside_dialog(node: ET.Element, root: ET.Element) -> bool:
    current: ET.Element | None = node
    for _ in range(6):
        if current is None:
            break
        if "dialog" in _class_short(current.get("class") or ""):
            return True
        current = _find_parent(root, current)
    return False


def _infer_widget_type(node: ET.Element, root: ET.Element) -> str:
    clazz = _class_short(node.get("class") or "")
    rid = (node.get("resource-id") or "").lower()
    text = (node.get("text") or "").strip().lower()
    desc = (node.get("content-desc") or "").strip().lower()

    if any(k in rid for k in ("upload", "file", "picker", "attach")):
        return "upload"

    for keywords, wtype in _WIDGET_CLASS_RULES:
        if any(k in clazz for k in keywords):
            if wtype == "input" and _is_rich_text(node):
                return "rich_text"
            return wtype

    if _is_inside_dialog(node, root):
        label = text or desc
        if label in _CONFIRM_TEXTS or any(t in label for t in _CONFIRM_TEXTS):
            return "confirm_dialog"

    if node.get("long-clickable") == "true" and "edittext" not in clazz:
        return "hover"

    return "click"


def _map_widget_to_step_type(widget_type: str) -> str:
    return {
        "select": "select",
        "confirm_dialog": "confirm_dialog",
        "input": "input",
        "rich_text": "rich_text",
        "upload": "upload",
        "hover": "hover",
        "checkbox": "click",
        "switch": "click",
        "click": "click",
    }.get(widget_type, "click")


def _build_absolute_xpath(node: ET.Element, root: ET.Element) -> str:
    parts: list[str] = []
    current: ET.Element | None = node
    for _ in range(12):
        if current is None:
            break
        clazz = _class_short(current.get("class") or "") or "*"
        parent = _find_parent(root, current)
        idx = 1
        if parent is not None:
            same = [c for c in list(parent) if _class_short(c.get("class") or "") == clazz]
            if len(same) > 1:
                idx = same.index(current) + 1
        parts.insert(0, f"{clazz}[{idx}]")
        current = parent
    return "/hierarchy/" + "/".join(parts) if parts else ""


def _build_relative_xpath(node: ET.Element, root: ET.Element) -> str:
    parts: list[str] = []
    current = node
    for _ in range(8):
        if current is None:
            break
        clazz = (current.get("class") or "").split(".")[-1]
        idx = 1
        parent = _find_parent(root, current)
        if parent is not None:
            same = [c for c in list(parent) if (c.get("class") or "").split(".")[-1] == clazz]
            if len(same) > 1:
                idx = same.index(current) + 1
        parts.insert(0, f"{clazz}[{idx}]" if clazz else "*")
        if parent is None:
            break
        current = parent
    return "//" + "/".join(parts) if parts else ""


def _build_locators(node: ET.Element, root: ET.Element, text: str, desc: str, rid: str, clazz: str) -> dict:
    locators: dict = {}
    if rid and not _is_generic_rid(rid):
        short = rid.split(":id/")[-1] if ":id/" in rid else rid
        if not _is_dynamic(short):
            locators["id"] = short
            locators["resource_id"] = rid
            from uiselector_helper import build_uiselector
            ui_sel = build_uiselector(node, text, desc, rid)
            if ui_sel:
                locators["uiselector"] = ui_sel
    if text and not _is_dynamic(text):
        escaped = text.replace('"', '\\"')
        locators["text"] = text
        locators["xpath_text"] = f'//*[@text="{escaped}"]'
    if desc and not _is_dynamic(desc):
        escaped = desc.replace('"', '\\"')
        locators["content_desc"] = desc
        locators["xpath_desc"] = f'//*[@content-desc="{escaped}"]'
    if clazz:
        short_cls = clazz.split(".")[-1]
        if short_cls and not _is_dynamic(short_cls):
            locators["class_name"] = short_cls
    bounds = node.get("bounds") or ""
    if bounds:
        locators["bounds"] = bounds
    rel = _build_relative_xpath(node, root)
    if rel and len(rel) < 256:
        locators["relative_xpath"] = rel
    abs_xp = _build_absolute_xpath(node, root)
    if abs_xp and len(abs_xp) < 512:
        locators["absolute_xpath"] = abs_xp
    from relative_locator import build_relative_locators
    locators.update(build_relative_locators(node, root, text, desc, rid))
    primary_type, primary_val = _locator_from_node(text, desc, rid)
    if primary_val:
        locators["primary_type"] = primary_type
        locators["primary_value"] = primary_val
    return locators


def _node_locator_score(node: ET.Element) -> int:
    rid = (node.get("resource-id") or "").strip()
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    score = 0
    if rid and not _is_generic_rid(rid):
        short = rid.split(":id/")[-1] if ":id/" in rid else rid
        if not _is_dynamic(short):
            score += 100
    if desc and not _is_dynamic(desc):
        score += 50
    if text and not _is_dynamic(text):
        score += 40
    if node.get("clickable") == "true":
        score += 10
    return score


def _node_pick_score(node: ET.Element, area_ratio: float) -> int:
    score = _node_locator_score(node)
    rid = (node.get("resource-id") or "").strip()
    clazz = node.get("class") or ""
    if _is_generic_rid(rid):
        score -= 220
    if area_ratio > 0.75 and _is_layout_container(clazz):
        if not (node.get("text") or "").strip() and not (node.get("content-desc") or "").strip():
            score -= 180
    elif area_ratio > 0.45:
        score -= int(area_ratio * 50)
    if node.get("clickable") == "true":
        score += 25
    if node.get("focusable") == "true":
        score += 8
    return score


def _is_weak_inspect_result(result: dict) -> bool:
    if not result.get("valid"):
        return True
    locators = result.get("locators") or {}
    rid = str(locators.get("resource_id") or locators.get("id") or result.get("locator_value") or "")
    if _is_generic_rid(rid):
        return True
    name = str(result.get("element_name") or result.get("display_name") or "").lower()
    if name in ("content", "ui_element", "framelayout_element"):
        return True
    bounds = str(result.get("bounds") or locators.get("bounds") or "")
    parsed = _parse_bounds(bounds)
    ui_w = int(result.get("ui_width") or 0)
    ui_h = int(result.get("ui_height") or 0)
    if parsed and ui_w > 0 and ui_h > 0:
        ratio = ((parsed[2] - parsed[0]) * (parsed[3] - parsed[1])) / (ui_w * ui_h)
        clazz = result.get("class") or ""
        if ratio > 0.72 and _is_layout_container(str(clazz)):
            if not (result.get("text") or "").strip():
                return True
    return False


def dump_ui_with_retry(serial: str, attempts: int = 2) -> str:
    import time as _time
    last = ""
    for _ in range(max(1, attempts)):
        last = dump_ui(serial)
        if last:
            return last
        _time.sleep(0.15)
    return last


def _schedule_ui_refresh(serial: str, *, force: bool = False) -> None:
    now = time.time()
    if tap_priority_active(serial):
        return
    if interaction_guard_blocks_dump(serial):
        return
    if not force:
        last = _last_refresh_at.get(serial, 0)
        if now - last < MIN_REFRESH_INTERVAL:
            return
    with _ui_dump_lock:
        if serial in _refresh_inflight and not force:
            return
        _refresh_inflight.add(serial)
    _last_refresh_at[serial] = now

    def job() -> None:
        try:
            from adb_client import u2_guard_blocks_dump, tap_priority_active
            if u2_guard_blocks_dump(serial) or tap_priority_active(serial):
                return
            import tempfile
            from pathlib import Path
            local = Path(tempfile.gettempdir()) / f"atp_ui_{serial}.xml"
            ok = try_adb_dump_ui(serial, str(local))
            if ok:
                xml = local.read_text(encoding="utf-8", errors="ignore")
                if xml:
                    with _ui_dump_lock:
                        _ui_dump_cache[serial] = (time.time(), xml)
        finally:
            with _ui_dump_lock:
                _refresh_inflight.discard(serial)

    threading.Thread(target=job, daemon=True).start()


def dump_ui_cached(serial: str, *, allow_stale: bool = True, blocking: bool = True) -> str:
    """优先读缓存 UI 树，避免每次点击都同步 adb dump（约 3–5s）。"""
    now = time.time()
    cached: tuple[float, str] | None = None
    with _ui_dump_lock:
        cached = _ui_dump_cache.get(serial)
    if cached:
        age = now - cached[0]
        if age < CACHE_TTL_FRESH:
            return cached[1]
        if allow_stale and age < CACHE_TTL_STALE:
            return cached[1]
    if not blocking:
        _schedule_ui_refresh(serial, force=True)
        return cached[1] if cached and allow_stale else ""
    from adb_client import u2_guard_blocks_dump, tap_priority_active
    if u2_guard_blocks_dump(serial) or tap_priority_active(serial):
        if cached and allow_stale:
            return cached[1]
        time.sleep(0.35)
    xml = dump_ui(serial)
    if xml:
        with _ui_dump_lock:
            _ui_dump_cache[serial] = (time.time(), xml)
    _schedule_ui_refresh(serial)
    return xml


def warm_ui_cache(serial: str, blocking: bool = False) -> dict:
    """预热 UI 缓存；blocking=True 时同步 dump 并写入缓存。"""
    if blocking:
        invalidate_ui_cache(serial)
        xml = dump_ui_with_retry(serial, attempts=2)
        if xml:
            with _ui_dump_lock:
                _ui_dump_cache[serial] = (time.time(), xml)
            ctx = detect_context_from_xml(xml)
            return {
                "ok": True, "bytes": len(xml), "cached": True, "blocking": True,
                "page_context": ctx, "needs_context_switch": ctx in ("webview", "hybrid"),
            }
        return {"ok": False, "error": "ui_dump_failed", "blocking": True}
    with _ui_dump_lock:
        cached = _ui_dump_cache.get(serial)
    if cached and time.time() - cached[0] < CACHE_TTL_STALE:
        _schedule_ui_refresh(serial)
        return {"ok": True, "bytes": len(cached[1]), "cached": True}
    _schedule_ui_refresh(serial, force=True)
    return {"ok": True, "warming": True, "cached": bool(cached)}


def invalidate_ui_cache(serial: str) -> None:
    with _ui_dump_lock:
        _ui_dump_cache.pop(serial, None)


def _best_node_at_point(root: ET.Element, x: int, y: int) -> ET.Element | None:
    """在点击点命中节点中，优先选择面积较小且定位信息丰富的控件。"""
    screen_w, screen_h = _hierarchy_size(root)
    hits: list[tuple[int, float, int, ET.Element]] = []
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        if not _point_in_bounds(x, y, bounds):
            continue
        area = _bounds_area(bounds)
        ratio = _node_area_ratio(node, screen_w, screen_h)
        pick_score = _node_pick_score(node, ratio)
        hits.append((area, ratio, -pick_score, node))
    if not hits:
        return _nearby_identifiable_node(root, x, y)

    hits.sort(key=lambda item: (item[0], item[1], item[2]))
    min_area = hits[0][0]
    area_cap = max(min_area * 6, min_area + 1)
    candidates = [item for item in hits if item[0] <= area_cap or -item[2] >= 20]

    best_node: ET.Element | None = None
    best_score = -10**9
    for _area, _ratio, neg_score, node in candidates:
        score = -neg_score
        if score > best_score:
            best_score = score
            best_node = node

    if best_node is not None and best_score <= -120:
        nearby = _nearby_identifiable_node(root, x, y)
        if nearby is not None:
            return nearby
    return best_node or hits[0][3]


def _nearby_identifiable_node(root: ET.Element, x: int, y: int, radius: int = 48) -> ET.Element | None:
    """精确坐标未命中时，在邻近区域找可定位控件。"""
    best: ET.Element | None = None
    best_key = (10**9, -1)
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        parsed = _parse_bounds(bounds)
        if not parsed:
            continue
        x1, y1, x2, y2 = parsed
        if x < x1 - radius or x > x2 + radius or y < y1 - radius or y > y2 + radius:
            continue
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        dist = abs(cx - x) + abs(cy - y)
        score = _node_locator_score(node)
        if score <= 0 and node.get("clickable") != "true":
            continue
        if _is_generic_rid(node.get("resource-id") or ""):
            continue
        key = (dist, -score)
        if key < best_key:
            best_key = key
            best = node
    return best


def _hierarchy_size(root: ET.Element) -> tuple[int, int]:
    max_x = 0
    max_y = 0
    for node in root.iter("node"):
        parsed = _parse_bounds(node.get("bounds") or "")
        if not parsed:
            continue
        max_x = max(max_x, parsed[2])
        max_y = max(max_y, parsed[3])
    return max_x, max_y


def _adb_display_size(serial: str) -> tuple[int, int]:
    try:
        r = adb_shell(serial, "wm", "size", timeout=6)
        for line in (r.stdout or b"").decode("utf-8", errors="ignore").splitlines():
            if "Physical size:" in line or "Override size:" in line:
                m = re.search(r"(\d+)x(\d+)", line)
                if m:
                    return int(m.group(1)), int(m.group(2))
    except Exception:
        pass
    return 0, 0


def _scale_coords(x: int, y: int, from_w: int, from_h: int, to_w: int, to_h: int) -> tuple[int, int]:
    if from_w <= 0 or from_h <= 0 or to_w <= 0 or to_h <= 0:
        return x, y
    if from_w == to_w and from_h == to_h:
        return x, y
    return int(round(x * to_w / from_w)), int(round(y * to_h / from_h))


LOCATOR_CHAIN_PRIORITY = (
    "id",
    "resource_id",
    "uiselector",
    "content_desc",
    "xpath_desc",
    "text",
    "xpath_text",
    "relative_xpath",
    "parent_index",
    "anchor_adjacent",
    "region_locator",
    "class_name",
    "bounds",
    "screen_ratio",
    "ocr",
)

_DYNAMIC_TEXT_HINTS = (
    re.compile(r"^\d{4,}$"),
    re.compile(r"验证码|captcha|code", re.I),
    re.compile(r"^\d{1,2}:\d{2}$"),
    re.compile(r"^[\d\s\-+()]{7,}$"),
)


def _risk_level_max(a: str, b: str) -> str:
    order = {"low": 0, "medium": 1, "high": 2}
    return a if order.get(a, 0) >= order.get(b, 0) else b


def _is_dynamic_text(text: str) -> bool:
    if not text or _is_dynamic(text):
        return True
    for pat in _DYNAMIC_TEXT_HINTS:
        if pat.search(text.strip()):
            return True
    return False


def _count_locator_matches(root: ET.Element, key: str, value: str) -> int:
    if not value:
        return 0
    count = 0
    for node in root.iter("node"):
        if _locator_exists_on_node(node, key, str(value)):
            count += 1
    return count


def _locator_exists_on_node(node: ET.Element, key: str, value: str) -> bool:
    val = str(value).strip()
    if key in ("id", "resource_id"):
        rid = (node.get("resource-id") or "").strip()
        short = val.split(":id/")[-1] if ":id/" in val else val
        return bool(rid and (rid == val or rid.endswith("/" + short) or short in rid))
    if key in ("text", "ocr"):
        text = (node.get("text") or "").strip()
        return bool(text and (text == val or val in text or text in val))
    if key in ("content_desc", "xpath_desc"):
        desc = (node.get("content-desc") or "").strip()
        return bool(desc and (desc == val or val in desc))
    if key == "class_name":
        clazz = (node.get("class") or "").strip()
        return bool(clazz and val in clazz)
    return False


def _build_screen_ratio(bounds: str, ui_w: int, ui_h: int) -> str:
    parsed = _parse_bounds(bounds)
    if not parsed or ui_w <= 0 or ui_h <= 0:
        return ""
    x1, y1, x2, y2 = parsed
    cx = (x1 + x2) / 2 / ui_w
    cy = (y1 + y2) / 2 / ui_h
    return f"{cx:.4f},{cy:.4f}"


def _pick_primary_locator(locators: dict, platform: str = "android", root: ET.Element | None = None) -> tuple[str, str]:
    return pick_primary_from_locators(locators, platform, root)


def _build_locator_chain(locators: dict, platform: str = "android", root: ET.Element | None = None) -> list[dict]:
    return build_recommended_chain(locators, platform, root)


def _assess_locator_risks(
    root: ET.Element | None,
    node: ET.Element | None,
    locators: dict,
    result: dict,
) -> dict:
    reasons: list[str] = []
    tags: list[str] = []
    level = "low"
    if node is None:
        return {"risk_level": "high", "risk_tags": ["no_node"], "risk_reasons": ["未命中控件节点"]}

    rid = (node.get("resource-id") or "").strip()
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    clazz = node.get("class") or ""

    if _is_generic_rid(rid) or _is_weak_inspect_result(result):
        reasons.append("命中页面容器节点，不宜作为主定位")
        tags.append("container")
        level = "high"

    if not rid and not text and not desc:
        reasons.append("无 resourceId / 文本 / content-desc")
        tags.append("no_unique_attr")
        level = _risk_level_max(level, "high")

    if _class_short(clazz) in _LAYOUT_CONTAINER_CLASSES and not text and not desc:
        reasons.append("仅依赖布局容器 className")
        tags.append("layout_only")
        level = _risk_level_max(level, "medium")

    if text and _is_dynamic_text(text):
        reasons.append("文本疑似动态内容，禁止作为主定位")
        tags.append("dynamic_text")
        level = _risk_level_max(level, "medium")

    if root is not None:
        for key in ("id", "resource_id", "text", "content_desc"):
            val = locators.get(key)
            if not val:
                continue
            cnt = _count_locator_matches(root, key, str(val))
            if cnt > 1:
                reasons.append(f"「{key}」在当前页面匹配 {cnt} 处，存在重复")
                tags.append("duplicate")
                level = _risk_level_max(level, "medium")

    if result.get("context") in ("webview", "hybrid"):
        reasons.append("WebView 区域，可能需要切换上下文")
        tags.append("webview")
        level = _risk_level_max(level, "medium")

    if node.get("clickable") != "true" and node.get("focusable") != "true":
        tags.append("not_interactive")

    if locators.get("screen_ratio") and not locators.get("id"):
        tags.append("ratio_fallback")
        reasons.append("已生成屏幕比例坐标兜底")
        level = _risk_level_max(level, "medium")

    return {"risk_level": level, "risk_tags": tags, "risk_reasons": reasons}


def _enrich_inspect_metadata(root: ET.Element, node: ET.Element, result: dict) -> dict:
    locators = dict(result.get("locators") or {})
    ui_w = int(result.get("ui_width") or 0)
    ui_h = int(result.get("ui_height") or 0)
    bounds = result.get("bounds") or node.get("bounds") or ""
    ratio = _build_screen_ratio(bounds, ui_w, ui_h)
    if ratio:
        locators["screen_ratio"] = ratio
    result["locators"] = locators

    platform = str(result.get("platform") or "android")
    loc_type, loc_val = _pick_primary_locator(locators, platform, root)
    if loc_type:
        result["locator_type"] = loc_type
        result["locator_value"] = loc_val
    chain = _build_locator_chain(locators, platform, root)
    result["locator_chain"] = chain
    rec = next((c for c in chain if c.get("recommended")), None)
    if rec:
        result["recommend_reason"] = rec.get("recommend_reason", "")
        result["recommended_locator_type"] = rec.get("type")
        result["recommended_locator_value"] = rec.get("value")
    result.update(_assess_locator_risks(root, node, locators, result))
    return result


def _inspect_root_at_point(
    root: ET.Element,
    inspect_x: int,
    inspect_y: int,
    client_x: int,
    client_y: int,
    target_w: int,
    target_h: int,
    ref_w: int,
    ref_h: int,
) -> dict:
    result: dict = {
        "x": int(client_x),
        "y": int(client_y),
        "client_x": client_x,
        "client_y": client_y,
        "inspect_x": inspect_x,
        "inspect_y": inspect_y,
        "ui_width": target_w,
        "ui_height": target_h,
    }
    if ref_w > 0 and ref_h > 0:
        result["display_width"] = ref_w
        result["display_height"] = ref_h

    best_node = _best_node_at_point(root, inspect_x, inspect_y)
    if best_node is None:
        result["inspect_error"] = "no_node_at_point"
        return result

    text = (best_node.get("text") or "").strip()
    desc = (best_node.get("content-desc") or "").strip()
    rid = (best_node.get("resource-id") or "").strip()
    clazz = (best_node.get("class") or "").strip()
    bounds = best_node.get("bounds") or ""
    clickable = best_node.get("clickable") == "true"
    name = _element_name(text, desc, rid, clazz)
    loc_type, loc_val = _locator_from_node(text, desc, rid)
    locators = _build_locators(best_node, root, text, desc, rid, clazz)
    if not loc_val and locators:
        if locators.get("id"):
            loc_type, loc_val = "id", str(locators["id"])
        elif locators.get("content_desc"):
            loc_type, loc_val = "content_desc", str(locators["content_desc"])
        elif locators.get("xpath_desc"):
            loc_type, loc_val = "xpath", str(locators["xpath_desc"])
        elif locators.get("relative_xpath"):
            loc_type, loc_val = "xpath", str(locators["relative_xpath"])
    widget_type = _infer_widget_type(best_node, root)
    recognized = bool(name or loc_val or locators) and not _is_generic_rid(rid)
    result.update({
        "source": "uiautomator",
        "element_name": name,
        "display_name": _display_name(text, desc, rid),
        "locator_type": loc_type,
        "locator_value": loc_val,
        "locators": locators,
        "text": text or desc,
        "class": clazz,
        "bounds": bounds,
        "clickable": clickable,
        "valid": recognized,
        "widget_type": widget_type,
        "suggested_step_type": _map_widget_to_step_type(widget_type),
    })
    return _enrich_inspect_metadata(root, best_node, result)


def wait_page_ready(serial: str, timeout: float = 10.0, interval: float = 0.5) -> dict:
    """launch/navigate 后等待页面根节点就绪（M4-05）"""
    import time

    deadline = time.time() + max(1.0, timeout)
    while time.time() < deadline:
        xml = dump_ui_cached(serial, blocking=True) or dump_ui(serial)
        if xml and len(xml) > 120:
            try:
                root = ET.fromstring(xml)
                nodes = list(root.iter("node"))
                if len(nodes) >= 2:
                    return {"ok": True, "node_count": len(nodes)}
            except ET.ParseError:
                pass
        time.sleep(max(0.2, interval))
    return {"ok": False, "error": "page_not_ready"}


def inspect_point(
    serial: str,
    x: int,
    y: int,
    platform: str = "android",
    display_width: int = 0,
    display_height: int = 0,
    blocking: bool = False,
    agent_host: str = "",
    wda_port: int = 8100,
) -> dict:
    """根据点击坐标反查控件；无控件时 OCR/UI 文本兜底。"""
    result: dict = {
        "x": int(x),
        "y": int(y),
        "platform": platform,
        "context": "native",
        "source": "coordinate",
        "element_name": "",
        "display_name": "",
        "locator_type": "",
        "locator_value": "",
        "locators": {},
        "text": "",
        "valid": False,
        "needs_context_switch": False,
        "widget_type": "unknown",
        "suggested_step_type": "click",
    }
    if not serial:
        return result
    if platform == "ios":
        from ios_dump_helper import inspect_ios_point
        host = agent_host or os.environ.get("ATP_WDA_HOST", "127.0.0.1")
        port = int(wda_port or os.environ.get("ATP_WDA_PORT", "8100"))
        ios_result = inspect_ios_point(host, port, x, y)
        ios_result.setdefault("x", x)
        ios_result.setdefault("y", y)
        return ios_result
    if platform != "android":
        return result

    if blocking and tap_priority_active(serial):
        blocking = False

    xml = dump_ui_cached(serial, allow_stale=True, blocking=blocking)
    if not xml:
        if not blocking:
            _schedule_ui_refresh(serial, force=True)
            result["inspect_error"] = "cache_miss"
            return result
        result["inspect_error"] = "ui_dump_failed"
        return _ocr_screen_fallback(serial, x, y, result)

    def _run_inspect(xml_text: str) -> dict:
        try:
            root = ET.fromstring(xml_text)
        except ET.ParseError:
            return {"inspect_error": "parse_error"}

        ui_w, ui_h = _hierarchy_size(root)
        if ui_w > 0 and ui_h > 0:
            wm_w, wm_h = ui_w, ui_h
        else:
            wm_w, wm_h = _adb_display_size(serial)
        target_w = ui_w or wm_w
        target_h = ui_h or wm_h
        client_x, client_y = int(x), int(y)
        inspect_x, inspect_y = client_x, client_y
        ref_w = display_width if display_width > 0 else 0
        ref_h = display_height if display_height > 0 else 0
        if ref_w <= 0 or ref_h <= 0:
            ref_w, ref_h = wm_w, wm_h
        if target_w > 0 and target_h > 0 and ref_w > 0 and ref_h > 0:
            if abs(ref_w - target_w) > 8 or abs(ref_h - target_h) > 8:
                inspect_x, inspect_y = _scale_coords(client_x, client_y, ref_w, ref_h, target_w, target_h)

        partial = _inspect_root_at_point(
            root, inspect_x, inspect_y, client_x, client_y, target_w, target_h, ref_w, ref_h
        )
        out = {**result, **partial}
        out["context"] = context
        out["needs_context_switch"] = context in ("webview", "hybrid")
        return out

    context = detect_context_from_xml(xml)
    result["context"] = context
    result["needs_context_switch"] = context in ("webview", "hybrid")

    inspected = _run_inspect(xml)
    result.update(inspected)

    # 自动识别 WebView 上下文并切换后重试拾取
    try:
        root_for_ctx = ET.fromstring(xml)
        inspect_x = int(result.get("inspect_x") or x)
        inspect_y = int(result.get("inspect_y") or y)
        switched, sw_logs = auto_switch_for_inspect(
            serial, root_for_ctx, inspect_x, inspect_y, context, dict(result)
        )
        if switched:
            fresh_ctx_xml = dump_ui_with_retry(serial, attempts=2)
            if fresh_ctx_xml:
                retry_ctx = _run_inspect(fresh_ctx_xml)
                if not retry_ctx.get("inspect_error") or retry_ctx.get("valid"):
                    result.update(retry_ctx)
                    result["auto_context_switched"] = True
                    result["needs_context_switch"] = False
                    result["context"] = "webview"
                    result["context_auto_logs"] = sw_logs
                    result["context_hint"] = "已自动切换 WebView 上下文并重新识别"
    except ET.ParseError:
        pass

    if inspected.get("inspect_error") == "no_node_at_point":
        return _ocr_screen_fallback(serial, result.get("inspect_y", y), result.get("inspect_x", x), result)

    if blocking and _is_weak_inspect_result(result):
        invalidate_ui_cache(serial)
        fresh = dump_ui_with_retry(serial, attempts=2)
        if fresh and fresh != xml:
            retry = _run_inspect(fresh)
            if not _is_weak_inspect_result({**result, **retry}) or retry.get("valid"):
                result.update(retry)
        elif fresh:
            retry = _run_inspect(fresh)
            if retry.get("valid") and not _is_weak_inspect_result({**result, **retry}):
                result.update(retry)

    if _is_weak_inspect_result(result) and not result.get("inspect_error"):
        ocr = _ocr_screen_fallback(serial, result.get("inspect_x", x), result.get("inspect_y", y), dict(result))
        if ocr.get("valid") and not _is_weak_inspect_result(ocr):
            return ocr
        result["valid"] = False
        result["inspect_error"] = result.get("inspect_error") or "generic_container"

    return result


def _ocr_screen_fallback(serial: str, x: int, y: int, result: dict) -> dict:
    """无控件树/弱命中时，截图 OCR 兜底（M4-03）"""
    try:
        from ocr_helper import ocr_pick_at_point
        pick = ocr_pick_at_point(serial, x, y)
    except Exception:
        return _ocr_near_point(serial, x, y, result)
    if not pick.get("text"):
        return _ocr_near_point(serial, x, y, result)
    text = str(pick["text"]).strip()
    result.update({
        "source": "ocr_screen",
        "element_name": _element_name(text, "", ""),
        "display_name": text,
        "text": text,
        "locator_type": "ocr",
        "locator_value": text,
        "locators": {"ocr": text, "text": text},
        "valid": True,
        "suggested_step_type": "tap_ocr",
        "widget_type": "text",
        "risk_level": "medium",
        "risk_tags": ["ocr_fallback"],
    })
    return result


def _ocr_near_point(serial: str, x: int, y: int, result: dict) -> dict:
    """UI 树无命中时，从 dump 文本行找最近文本。"""
    xml = dump_ui_cached(serial, allow_stale=True, blocking=False) or dump_ui_cached(serial)
    if not xml:
        return result
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return result
    best_text = ""
    best_dist = 10**9
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        if not bounds:
            continue
        parsed = _parse_bounds(bounds)
        if not parsed:
            continue
        x1, y1, x2, y2 = parsed
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        dist = abs(cx - x) + abs(cy - y)
        if dist > 120:
            continue
        text = (node.get("text") or node.get("content-desc") or "").strip()
        if len(text) >= 2 and dist < best_dist:
            best_dist = dist
            best_text = text
    if best_text:
        result.update({
            "source": "ocr",
            "element_name": _element_name(best_text, "", ""),
            "display_name": best_text,
            "text": best_text,
            "locator_type": "ocr",
            "locator_value": best_text,
            "locators": {"ocr": best_text, "text": best_text},
            "valid": True,
            "widget_type": "click",
            "suggested_step_type": "tap_ocr",
        })
    return result


_UPLOAD_OCR_KEYWORDS = ("上传", "附件", "选择文件", "选择图片", "upload", "attach", "file", "browse")


def _ocr_upload_fallback(serial: str, x: int, y: int, result: dict) -> dict:
    """upload 控件 UI 树未命中时，OCR 兜底识别上传相关文案。"""
    out = _ocr_near_point(serial, x, y, result)
    if not out.get("valid"):
        return out
    text = (out.get("text") or out.get("display_name") or "").lower()
    if any(kw in text for kw in _UPLOAD_OCR_KEYWORDS):
        out.update({
            "widget_type": "upload",
            "suggested_step_type": "upload",
            "source": "ocr_upload",
        })
    return out


def _node_text_matches(node: ET.Element, expected: str) -> bool:
    if not expected:
        return False
    exp = expected.strip()
    for attr in ("text", "content-desc", "resource-id"):
        val = (node.get(attr) or "").strip()
        if not val:
            continue
        if val == exp or exp in val or val in exp:
            return True
    return False


def _xpath_needle(value: str) -> str:
    val = str(value or "").strip()
    if '"' in val:
        parts = val.split('"')
        if len(parts) >= 2 and parts[1]:
            return parts[1]
    return val


def _node_is_visible(node: ET.Element, root: ET.Element | None = None) -> bool:
    from relative_locator import node_is_effectively_visible
    return node_is_effectively_visible(node, root)


def _node_is_tappable(node: ET.Element, root: ET.Element | None = None) -> bool:
    if not _node_is_visible(node, root):
        return False
    return (
        node.get("clickable") == "true"
        or node.get("focusable") == "true"
        or node.get("long-clickable") == "true"
        or node.get("checkable") == "true"
    )


def _find_nodes_for_locator(root: ET.Element, key: str, value: str) -> list[ET.Element]:
    val = str(value or "").strip()
    if not val or key in ("bounds", "screen_ratio"):
        return []
    if key in ("parent_index", "anchor_adjacent", "region_locator"):
        from relative_locator import find_nodes_for_relative_locator
        return find_nodes_for_relative_locator(root, key, val)
    if key == "uiselector":
        from uiselector_helper import find_nodes_for_uiselector
        return find_nodes_for_uiselector(root, val)
    if key in ("xpath_desc", "xpath_text", "relative_xpath", "absolute_xpath"):
        needle = _xpath_needle(val)
        nodes = [n for n in root.iter("node") if _node_text_matches(n, needle)]
        if nodes:
            return [n for n in nodes if _node_is_visible(n, root)]
    if key == "xpath_desc":
        val = _xpath_needle(val)
        key = "content_desc"
    if key == "xpath_text":
        val = _xpath_needle(val)
        key = "text"
    nodes: list[ET.Element] = []
    for node in root.iter("node"):
        if _locator_exists_on_node(node, key, val) and _node_is_visible(node, root):
            nodes.append(node)
    return nodes


def evaluate_locator(root: ET.Element, key: str, value: str) -> dict:
    """评估单条定位：存在 / 可见 / 可点击。"""
    val = str(value or "").strip()
    result: dict = {
        "type": key,
        "value": val,
        "found": False,
        "visible": False,
        "clickable": False,
        "match_count": 0,
        "reason": "",
        "bounds": "",
    }
    if not val:
        result["reason"] = "empty_value"
        return result
    if key == "bounds":
        parsed = _parse_bounds(val)
        if not parsed:
            result["reason"] = "invalid_bounds"
            return result
        nodes = []
        for node in root.iter("node"):
            nb = _parse_bounds(node.get("bounds") or "")
            if nb == parsed:
                nodes.append(node)
        result["match_count"] = len(nodes)
        if not nodes:
            result["reason"] = "not_found"
            return result
        tappable = [n for n in nodes if _node_is_tappable(n, root)]
        visible = [n for n in nodes if _node_is_visible(n, root)]
        if tappable:
            result.update({"found": True, "visible": True, "clickable": True, "bounds": val})
        elif visible:
            result.update({"found": True, "visible": True, "reason": "not_clickable", "bounds": val})
        else:
            result["reason"] = "not_visible"
        return result
    if key == "screen_ratio":
        parts = [p.strip() for p in val.split(",")]
        if len(parts) >= 2:
            try:
                float(parts[0])
                float(parts[1])
                result.update({"found": True, "visible": True, "clickable": True, "match_count": 1})
            except ValueError:
                result["reason"] = "invalid_ratio"
        else:
            result["reason"] = "invalid_ratio"
        return result
    nodes = _find_nodes_for_locator(root, key, val)
    result["match_count"] = len(nodes)
    if not nodes:
        result["reason"] = "not_found"
        return result
    result["found"] = True
    tappable = [n for n in nodes if _node_is_tappable(n, root)]
    visible = [n for n in nodes if _node_is_visible(n, root)]
    if tappable:
        result["visible"] = True
        result["clickable"] = True
        result["bounds"] = tappable[0].get("bounds") or ""
    elif visible:
        result["visible"] = True
        result["reason"] = "not_clickable"
        result["bounds"] = visible[0].get("bounds") or ""
    else:
        result["reason"] = "not_visible"
        result["bounds"] = nodes[0].get("bounds") or ""
    return result


def _locator_exists_in_tree(root: ET.Element, key: str, value: str) -> bool:
    return bool(evaluate_locator(root, key, value).get("found"))


def _ordered_locator_keys(locators: dict, chain: list | None) -> list[str]:
    ordered: list[str] = []
    if chain:
        for item in chain:
            if isinstance(item, dict):
                if item.get("enabled") is False:
                    continue
                key = str(item.get("type") or item.get("key") or "")
            else:
                key = str(item)
            if key and key not in ordered:
                ordered.append(key)
    if not ordered:
        ordered = [k for k in LOCATOR_CHAIN_PRIORITY if k in locators]
        for key in locators:
            if key not in ordered and not str(key).startswith("primary_"):
                ordered.append(key)
    return ordered


def validate_locators_on_screen(serial: str, locators: dict, chain: list | None = None) -> dict:
    """在当前 UI 树中校验定位器是否可命中，按优先级依次尝试并记录过程。"""
    if not locators:
        return {"valid": False, "error": "empty_locators", "attempts": []}
    xml = dump_ui_cached(serial, blocking=True) or dump_ui(serial)
    if not xml:
        return {"valid": False, "error": "ui_dump_failed", "attempts": []}
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return {"valid": False, "error": "xml_parse_failed", "attempts": []}

    ordered_keys = _ordered_locator_keys(locators, chain)

    attempts: list[dict] = []
    last_found_not_clickable: dict | None = None
    for key in ordered_keys:
        val = locators.get(key)
        if not val:
            continue
        ev = evaluate_locator(root, key, str(val))
        attempts.append(ev)
        if ev.get("clickable"):
            return {
                "valid": True,
                "matched_by": key,
                "locator_key": key,
                "attempts": attempts,
                "bounds": ev.get("bounds") or "",
            }
        if ev.get("found") and not ev.get("clickable"):
            last_found_not_clickable = ev
    if last_found_not_clickable:
        return {
            "valid": False,
            "error": "not_clickable",
            "matched_by": last_found_not_clickable.get("type"),
            "attempts": attempts,
        }
    return {"valid": False, "error": "not_found", "attempts": attempts}
