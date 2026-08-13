"""录制步骤增强：坐标反查控件、多维度定位采集、WebView 上下文识别、OCR 兜底"""

import os
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

from adb_client import adb_shell, tap_priority_active, interaction_guard_blocks_dump
from ai_locator import dump_ui
from ui_dump_helper import dump_ui_xml, try_dump_ui_xml, get_last_dump_meta, dump_ui_text
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
CACHE_TTL_FRESH = 6.0
CACHE_TTL_STALE = 30.0
MIN_REFRESH_INTERVAL = 2.5


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


# 设置页分段选项：智能 / 1 / 2 / 3 … 属稳定业务文案，拾取时不能当动态噪声丢掉
_OPTION_LIKE_LABEL_RE = re.compile(
    r"^(?:[0-9]{1,2}|智能|自动|手动|关闭|开启|打开|低|中|高|强|弱|标准|安静|强力|自定义)$"
)


def _is_option_like_label(value: str) -> bool:
    s = (value or "").strip()
    if not s:
        return False
    if _OPTION_LIKE_LABEL_RE.match(s):
        return True
    # 其它极短中文/字母选项（≤4），排除明显动态串
    if 1 <= len(s) <= 4 and re.fullmatch(r"[\u4e00-\u9fffA-Za-z0-9]+", s):
        if re.fullmatch(r"\d{1,2}", s):
            return True
        if not re.fullmatch(r"[a-f0-9]{4,}", s, re.I):
            return not _is_dynamic(s) or bool(re.fullmatch(r"\d{1,2}", s))
    return False


def _is_dynamic_for_pick(value: str) -> bool:
    """拾取打分用：短选项数字/枚举文案视为有效标签。"""
    if _is_option_like_label(value):
        return False
    return _is_dynamic(value)


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
    "recyclerview",
    "listview",
    "scrollview",
    "nestedscrollview",
    "horizontalscrollview",
    "viewpager",
    "viewpager2",
})

# 业务控件叶子：优先于大面积容器
_CONTROL_LEAF_CLASSES = frozenset({
    "button",
    "imagebutton",
    "textview",
    "edittext",
    "checkbox",
    "radiobutton",
    "switch",
    "switchcompat",
    "checkedtextview",
    "imageview",
    "chip",
    "floatingactionbutton",
    "compoundbutton",
    "togglebutton",
    "seekbar",
    "ratingbar",
    "spinner",
    "appcompatbutton",
    "appcompatedittext",
    "appcompattextview",
    "appcompatimageview",
    "appcompatimagebutton",
    "materialbutton",
    "materialtextview",
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


def _is_control_leaf(clazz: str) -> bool:
    return _class_short(clazz) in _CONTROL_LEAF_CLASSES


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
    def _one_line(s: str) -> str:
        return " ".join((s or "").replace("\u0000", " ").split())[:64]

    if text.strip():
        return _one_line(text)
    if desc.strip():
        return _one_line(desc)
    if rid:
        return rid.split(":id/")[-1] if ":id/" in rid else rid.split("/")[-1]
    return "未知控件"


def _locator_from_node(text: str, desc: str, rid: str) -> tuple[str, str]:
    if rid and not _is_generic_rid(rid) and not _is_dynamic(rid.split(":id/")[-1] if ":id/" in rid else rid):
        if ":id/" in rid:
            return "id", rid.split(":id/")[-1]
        return "id", rid
    if text and (not _is_dynamic(text) or _is_option_like_label(text)):
        escaped = text.replace('"', '\\"')
        return "xpath", f'//*[@text="{escaped}"]'
    if desc and (not _is_dynamic(desc) or _is_option_like_label(desc)):
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
        parent = _find_parent(root, current)
        # hierarchy 本身不是控件节点，不再写入 *[1]
        if parent is None or str(getattr(parent, "tag", "")) == "hierarchy":
            clazz = _class_short(current.get("class") or "") or "*"
            idx = 1
            if parent is not None:
                same = [c for c in list(parent) if _class_short(c.get("class") or "") == clazz]
                if len(same) > 1:
                    idx = same.index(current) + 1
            parts.insert(0, f"{clazz}[{idx}]")
            break
        clazz = _class_short(current.get("class") or "") or "*"
        same = [c for c in list(parent) if _class_short(c.get("class") or "") == clazz]
        idx = same.index(current) + 1 if len(same) > 1 else 1
        parts.insert(0, f"{clazz}[{idx}]")
        current = parent
    return "/hierarchy/" + "/".join(parts) if parts else ""


def _build_relative_xpath(node: ET.Element, root: ET.Element) -> str:
    parts: list[str] = []
    current = node
    for _ in range(12):
        if current is None:
            break
        parent = _find_parent(root, current)
        clazz = (current.get("class") or "").split(".")[-1] or "*"
        idx = 1
        if parent is not None and str(getattr(parent, "tag", "")) != "hierarchy":
            same = [c for c in list(parent) if (c.get("class") or "").split(".")[-1] == clazz]
            if len(same) > 1:
                idx = same.index(current) + 1
            parts.insert(0, f"{clazz}[{idx}]")
            current = parent
            continue
        if parent is not None:
            same = [c for c in list(parent) if (c.get("class") or "").split(".")[-1] == clazz]
            if len(same) > 1:
                idx = same.index(current) + 1
        parts.insert(0, f"{clazz}[{idx}]")
        break
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
    if text and (not _is_dynamic(text) or _is_option_like_label(text)):
        escaped = text.replace('"', '\\"')
        locators["text"] = text
        locators["xpath_text"] = f'//*[@text="{escaped}"]'
    if desc and (not _is_dynamic(desc) or _is_option_like_label(desc)):
        escaped = desc.replace('"', '\\"')
        locators["content_desc"] = desc
        locators["xpath_desc"] = f'//*[@content-desc="{escaped}"]'
        short_cls = (clazz or "").split(".")[-1] or "*"
        locators["xpath_desc_contains"] = f'//{short_cls}[contains(@content-desc,"{escaped}")]'
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
    if desc and not _is_dynamic_for_pick(desc):
        score += 50
    if text and not _is_dynamic_for_pick(text):
        score += 40
    if node.get("clickable") == "true":
        score += 10
    return score


def _node_own_label(node: ET.Element) -> str:
    """稳定定位用标签（纯数字仍排除，避免脆弱 text=3）。"""
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    if text and not _is_dynamic(text):
        return text
    if desc and not _is_dynamic(desc):
        return desc
    return ""


def _node_pick_label(node: ET.Element) -> str:
    """拾取命中用标签：保留「智能/1/2/3」等短选项。"""
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    if text and not _is_dynamic_for_pick(text):
        return text
    if desc and not _is_dynamic_for_pick(desc):
        return desc
    return ""


def _is_tab_like_label(label: str) -> bool:
    s = (label or "").strip()
    if not s:
        return False
    if _is_option_like_label(s):
        return True
    if _is_dynamic(s):
        return False
    # 底栏常见短标签：定时 / 教程 / 我的 …（过长文案或卡片描述不算）
    if 1 <= len(s) <= 4:
        return True
    return False


def _label_distance(node: ET.Element, x: int, y: int) -> int:
    parsed = _parse_bounds(node.get("bounds") or "")
    if not parsed:
        return 10**9
    x1, y1, x2, y2 = parsed
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    # 点在 bounds 内：距离为 0，避免偏心点击被 radius 误杀
    if x1 <= x <= x2 and y1 <= y <= y2:
        return 0
    return abs(cx - x) + abs(cy - y)


def _iter_labeled_candidates(nodes: list[ET.Element], x: int, y: int, radius: int) -> ET.Element | None:
    best: ET.Element | None = None
    best_key = (10**9, 10**9, -1, 10**9)
    for node in nodes:
        label = _node_pick_label(node)
        if not label:
            continue
        parsed = _parse_bounds(node.get("bounds") or "")
        if not parsed:
            continue
        x1, y1, x2, y2 = parsed
        inside = x1 <= x <= x2 and y1 <= y <= y2
        dist = _label_distance(node, x, y)
        if not inside and dist > radius:
            continue
        area = max(1, (x2 - x1) * (y2 - y1))
        # 短选项/底栏标签优先；同距离优先更小面积
        tab_bonus = 1 if (_is_tab_like_label(label) or _is_option_like_label(label)) else 0
        key = (0 if inside else 1, dist, -tab_bonus, area)
        if key < best_key:
            best_key = key
            best = node
    return best


def _is_bottom_strip(node: ET.Element, screen_w: int, screen_h: int) -> bool:
    """全宽底部导航条（含异形底栏外层容器）。"""
    parsed = _parse_bounds(node.get("bounds") or "")
    if not parsed or screen_w <= 0 or screen_h <= 0:
        return False
    x1, y1, x2, y2 = parsed
    w, h = max(1, x2 - x1), max(1, y2 - y1)
    return (w / screen_w) >= 0.82 and (h / screen_h) <= 0.28 and (y1 / screen_h) >= 0.68


def _node_pick_score(node: ET.Element, area_ratio: float, screen_w: int = 0, screen_h: int = 0) -> int:
    score = _node_locator_score(node)
    rid = (node.get("resource-id") or "").strip()
    clazz = node.get("class") or ""
    label = _node_pick_label(node)
    if _is_generic_rid(rid):
        score -= 220

    # 面积惩罚：大块区域即使有 content-desc / clickable 也不该压过小按钮
    if area_ratio > 0.55:
        score -= int(180 + area_ratio * 120)
    elif area_ratio > 0.28:
        score -= int(80 + area_ratio * 100)
    elif area_ratio > 0.12:
        score -= int(area_ratio * 90)

    if _is_layout_container(clazz):
        if not label:
            score -= 140 if area_ratio > 0.08 else 60
        else:
            # 有文案的大卡片/整行容器：仍降权，避免点按钮却拿到整卡
            if area_ratio > 0.18:
                score -= 90
            elif area_ratio > 0.08:
                score -= 45

    if _is_control_leaf(clazz):
        score += 55
        # 小面积叶子再加分
        if area_ratio <= 0.08:
            score += 35
        elif area_ratio <= 0.15:
            score += 18

    # 分段选项芯片：智能/1/2/3 等，强优先于整块设置行
    if _is_option_like_label(label) and area_ratio <= 0.1:
        score += 95
        if node.get("clickable") == "true" or node.get("checkable") == "true" or node.get("selected") == "true":
            score += 20

    # 全宽底栏容器：即使面积比不大也要重罚，避免吞掉「定时/教程」
    if screen_w > 0 and screen_h > 0 and _is_bottom_strip(node, screen_w, screen_h):
        if not label:
            score -= 120
        elif not _is_tab_like_label(label):
            score -= 60
        clazz_l = clazz.lower()
        if "bottomnavigation" in clazz_l or "navigationbar" in clazz_l:
            score -= 40
    if node.get("clickable") == "true":
        # 仅可点、无标识的大节点加分要收敛，避免 score≥20 绕过面积过滤
        if label or (rid and not _is_generic_rid(rid)):
            if area_ratio > 0.22:
                score += 8
            else:
                score += 28
        else:
            score += 4 if area_ratio > 0.15 else 8
    if node.get("focusable") == "true" and area_ratio <= 0.2:
        score += 8
    # 短标签（底栏文案）额外加分
    if _is_tab_like_label(label):
        score += 35
    return score


def _is_weak_inspect_result(result: dict) -> bool:
    if not result.get("valid"):
        return True
    locators = result.get("locators") or {}
    rid = str(locators.get("resource_id") or locators.get("id") or result.get("locator_value") or "")
    if _is_generic_rid(rid):
        return True
    name = str(result.get("element_name") or result.get("display_name") or "").lower()
    weak_names = (
        "content", "ui_element", "framelayout_element", "linearlayout_element",
        "relativelayout_element", "constraintlayout_element", "view_element",
        "imageview_element", "image_element", "viewgroup_element",
        "bottomnavigationview_element", "navigationview_element",
        "recyclerview_element", "listview_element", "scrollview_element",
    )
    if name in weak_names:
        return True
    text = str(result.get("text") or "").strip()
    desc = str((locators.get("content_desc") or "")).strip()
    has_label = bool(text or desc or locators.get("text"))
    bounds = str(result.get("bounds") or locators.get("bounds") or "")
    parsed = _parse_bounds(bounds)
    ui_w = int(result.get("ui_width") or 0)
    ui_h = int(result.get("ui_height") or 0)
    if parsed and ui_w > 0 and ui_h > 0:
        x1, y1, x2, y2 = parsed
        w, h = max(1, x2 - x1), max(1, y2 - y1)
        ratio = (w * h) / (ui_w * ui_h)
        clazz = str(result.get("class") or "")
        # 覆盖大半屏：一律弱命中（含大 TextView「扫描中…」），触发收缩/OCR
        if ratio > 0.25:
            return True
        # 点到大卡片/整块容器 → 视为弱命中
        if ratio > 0.15 and _is_layout_container(clazz):
            return True
        if ratio > 0.28 and not _is_control_leaf(clazz):
            return True
        # 全宽底条且无稳定文案 → 弱命中，触发近点/OCR 提升
        if (w / ui_w) >= 0.82 and (h / ui_h) <= 0.28 and (y1 / ui_h) >= 0.68 and not has_label:
            return True
        # 纯图标节点（ImageView）无 text/desc
        if "image" in clazz.lower() and not has_label:
            return True
    if not has_label and not rid:
        return True
    return False


def _prefer_smallest_under_point(
    root: ET.Element,
    node: ET.Element | None,
    x: int,
    y: int,
    screen_w: int = 0,
    screen_h: int = 0,
    max_ratio: float = 0.22,
) -> ET.Element | None:
    """大面积命中时，强制收缩到覆盖点击点的更小子孙（允许无文案叶子）。"""
    if node is None:
        return None
    if screen_w <= 0 or screen_h <= 0:
        screen_w, screen_h = _hierarchy_size(root)
    cur = node
    for _ in range(12):
        ratio = _node_area_ratio(cur, screen_w, screen_h)
        cur_area = _bounds_area(cur.get("bounds") or "")
        if cur_area <= 0:
            return cur
        if ratio <= max_ratio and (
            _is_control_leaf(cur.get("class") or "")
            or _node_pick_label(cur)
            or cur.get("clickable") == "true"
        ):
            return cur
        best = None
        best_key = None
        for child in cur.iter("node"):
            if child is cur:
                continue
            bounds = child.get("bounds") or ""
            if not _point_in_bounds(x, y, bounds):
                continue
            area = _bounds_area(bounds)
            if area <= 0 or area >= cur_area:
                continue
            # 必须明显更小，避免同层几乎等大的兄弟干扰
            if area > int(cur_area * 0.92):
                continue
            cr = _node_area_ratio(child, screen_w, screen_h)
            clazz = child.get("class") or ""
            label = _node_pick_label(child)
            # 跳过仍是大半屏且无标识的布局壳
            if _is_layout_container(clazz) and cr > 0.35 and not label:
                continue
            score = _node_pick_score(child, cr, screen_w, screen_h)
            leaf_rank = 0 if _is_control_leaf(clazz) else 1
            label_rank = 0 if label else 1
            key = (area, cr, label_rank, leaf_rank, -score)
            if best_key is None or key < best_key:
                best_key = key
                best = child
        if best is None or best is cur:
            # 全局再扫一遍更小命中
            global_best = None
            global_key = None
            for cand in root.iter("node"):
                if cand is cur:
                    continue
                bounds = cand.get("bounds") or ""
                if not _point_in_bounds(x, y, bounds):
                    continue
                area = _bounds_area(bounds)
                if area <= 0 or area >= int(cur_area * 0.7):
                    continue
                cr = _node_area_ratio(cand, screen_w, screen_h)
                if cr > max_ratio and not _node_pick_label(cand) and not _is_control_leaf(cand.get("class") or ""):
                    continue
                score = _node_pick_score(cand, cr, screen_w, screen_h)
                key = (area, cr, 0 if _node_pick_label(cand) else 1, 0 if _is_control_leaf(cand.get("class") or "") else 1, -score)
                if global_key is None or key < global_key:
                    global_key = key
                    global_best = cand
            return global_best or cur
        cur = best
    return cur


def _build_parent_map(root: ET.Element) -> dict[ET.Element, ET.Element]:
    parent: dict[ET.Element, ET.Element] = {}
    for el in root.iter():
        for child in list(el):
            parent[child] = el
    return parent


def _is_meaningful_tight_hit(node: ET.Element) -> bool:
    """可作为收缩目标的「真正控件」：叶子/可点/带文案/稳定 id。"""
    clazz = node.get("class") or ""
    label = _node_pick_label(node)
    rid = (node.get("resource-id") or "").strip()
    if _is_control_leaf(clazz):
        return True
    if label:
        return True
    if rid and not _is_generic_rid(rid):
        return True
    if node.get("clickable") == "true" and not _is_layout_container(clazz):
        return True
    if node.get("checkable") == "true" or node.get("selected") == "true":
        return True
    return False


def _shrink_to_tightest_hit(
    root: ET.Element,
    node: ET.Element | None,
    x: int,
    y: int,
    screen_w: int = 0,
    screen_h: int = 0,
) -> ET.Element | None:
    """
    点落在大卡片/容器内时，收缩到覆盖该点的更小叶子控件。
    避免「点了按钮却识别整块区域」。
    """
    if node is None:
        return None
    if screen_w <= 0 or screen_h <= 0:
        screen_w, screen_h = _hierarchy_size(root)

    parent_bounds = node.get("bounds") or ""
    parent_area = _bounds_area(parent_bounds)
    parent_ratio = _node_area_ratio(node, screen_w, screen_h)
    # 本身已是小叶子且面积不大，无需再收缩
    if _is_control_leaf(node.get("class") or "") and parent_ratio <= 0.12:
        return node
    if parent_area <= 0:
        return node

    best = node
    best_key: tuple | None = None
    for child in node.iter("node"):
        if child is node:
            continue
        bounds = child.get("bounds") or ""
        if not _point_in_bounds(x, y, bounds):
            continue
        area = _bounds_area(bounds)
        if area <= 0 or area >= parent_area:
            continue
        ratio = _node_area_ratio(child, screen_w, screen_h)
        meaningful = _is_meaningful_tight_hit(child)
        # 父节点很大时：允许收缩到无文案的更小子节点（仍跳过超大空壳布局）
        if not meaningful:
            if parent_ratio <= 0.2:
                continue
            if _is_layout_container(child.get("class") or "") and ratio > 0.25 and not _node_pick_label(child):
                continue
        # 至少缩小到约 55% 才有意义（大父节点放宽到 85%）
        shrink_cap = 0.85 if parent_ratio > 0.2 else 0.55
        if area > int(parent_area * shrink_cap) and parent_ratio <= 0.2:
            continue
        # 仍是大半屏布局容器则跳过（父节点本身很大时允许再往下钻一层）
        if _is_layout_container(child.get("class") or "") and ratio > 0.18 and not _node_pick_label(child):
            if parent_ratio <= 0.25 or ratio >= parent_ratio * 0.9:
                continue
        score = _node_pick_score(child, ratio, screen_w, screen_h)
        # 优先更小面积；同分看 pick_score；选项芯片再优先
        opt_rank = -1 if _is_option_like_label(_node_pick_label(child)) else 0
        key = (area, ratio, -score, opt_rank, -1 if _is_control_leaf(child.get("class") or "") else 0)
        if best_key is None or key < best_key:
            best_key = key
            best = child

    # 若当前仍是大容器，再在「覆盖点击点」的全局更小命中中找一次
    if best is node and parent_ratio > 0.12:
        for cand in root.iter("node"):
            if cand is node:
                continue
            bounds = cand.get("bounds") or ""
            if not _point_in_bounds(x, y, bounds):
                continue
            if not _is_meaningful_tight_hit(cand) and parent_ratio <= 0.25:
                continue
            area = _bounds_area(bounds)
            if area <= 0 or area >= int(parent_area * 0.7):
                continue
            ratio = _node_area_ratio(cand, screen_w, screen_h)
            if ratio > 0.2 and _is_layout_container(cand.get("class") or "") and not _node_pick_label(cand):
                continue
            score = _node_pick_score(cand, ratio, screen_w, screen_h)
            opt_rank = -1 if _is_option_like_label(_node_pick_label(cand)) else 0
            key = (area, ratio, -score, opt_rank, -1 if _is_control_leaf(cand.get("class") or "") else 0)
            if best_key is None or key < best_key:
                best_key = key
                best = cand
    return best


def _promote_labeled_target(root: ET.Element, node: ET.Element | None, x: int, y: int) -> ET.Element | None:
    """
    点击落在底栏图标/空容器时，提升到附近带文案的 TextView / content-desc 节点。
    典型：ImageView(图标) + TextView(定时) 同属一个可点子项。
    大面积容器也会先尝试收缩到覆盖点击点的更小子控件。
    """
    if node is None:
        return None
    screen_w, screen_h = _hierarchy_size(root)
    # 大区域先收缩到更紧的叶子，避免整卡/整行被当成目标
    node = _shrink_to_tightest_hit(root, node, x, y, screen_w, screen_h) or node
    label = _node_pick_label(node)
    ratio = _node_area_ratio(node, screen_w, screen_h)
    # 短选项芯片直接返回
    if _is_option_like_label(label) and ratio <= 0.12:
        return node
    # 仅小面积短标签直接返回；大容器上的短 desc 继续往叶子找
    if _is_tab_like_label(label) and not (
        _is_layout_container(node.get("class") or "") and ratio > 0.08
    ):
        return node

    parent_map = _build_parent_map(root)
    bottom_zone = screen_h > 0 and y >= int(screen_h * 0.72)
    radius = 140 if bottom_zone else 72

    def _ui_nodes(els) -> list[ET.Element]:
        out = []
        for n in els:
            if n.get("bounds") is not None or n.get("class") is not None:
                out.append(n)
        return out

    # 1) 当前节点子树：优先覆盖点击点的短标签 / 叶子
    under_point = [
        n for n in _ui_nodes(node.iter())
        if _point_in_bounds(x, y, n.get("bounds") or "")
    ]
    hit = _iter_labeled_candidates(under_point or _ui_nodes(node.iter()), x, y, radius)
    if hit is not None:
        shrunk = _shrink_to_tightest_hit(root, hit, x, y, screen_w, screen_h)
        return shrunk or hit

    # 2) 向上找祖先，再在其子孙中找短标签（图标点击 → 同组「定时」）
    cur = node
    for _ in range(5):
        p = parent_map.get(cur)
        if p is None:
            break
        cur = p
        under = [
            n for n in _ui_nodes(cur.iter())
            if _point_in_bounds(x, y, n.get("bounds") or "")
        ]
        hit = _iter_labeled_candidates(under or _ui_nodes(cur.iter()), x, y, radius)
        if hit is not None:
            shrunk = _shrink_to_tightest_hit(root, hit, x, y, screen_w, screen_h)
            return shrunk or hit
        cur_ratio = _node_area_ratio(cur, screen_w, screen_h)
        if (
            cur.get("clickable") == "true"
            and _is_tab_like_label(_node_pick_label(cur))
            and not (_is_layout_container(cur.get("class") or "") and cur_ratio > 0.08)
        ):
            return cur

    # 3) 底部区域：全局近点短标签
    if bottom_zone:
        hit = _iter_labeled_candidates(_ui_nodes(root.iter("node")), x, y, radius)
        if hit is not None:
            shrunk = _shrink_to_tightest_hit(root, hit, x, y, screen_w, screen_h)
            return shrunk or hit
    return node


def _best_node_at_point(root: ET.Element, x: int, y: int) -> ET.Element | None:
    """在点击点命中节点中，优先选择面积较小的叶子控件，避免整块区域。"""
    screen_w, screen_h = _hierarchy_size(root)
    hits: list[tuple[int, float, int, ET.Element]] = []
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        if not _point_in_bounds(x, y, bounds):
            continue
        area = _bounds_area(bounds)
        ratio = _node_area_ratio(node, screen_w, screen_h)
        pick_score = _node_pick_score(node, ratio, screen_w, screen_h)
        pick_label = _node_pick_label(node)
        # 含子选项芯片的整块设置行：降权，逼迫命中「智能/3/2/1」
        option_kids = [
            n for n in node.iter("node")
            if n is not node and _is_option_like_label(_node_pick_label(n))
        ]
        if option_kids and not _is_option_like_label(pick_label):
            pick_score -= 80
        # 子树短标签加成：点在图标上也能抬升同组 TextView 父项
        if not pick_label:
            child_labels = [
                n for n in node.iter("node")
                if n is not node and _is_tab_like_label(_node_pick_label(n))
            ]
            if child_labels:
                pick_score += 45
        elif _is_option_like_label(pick_label):
            pick_score += 40
        hits.append((area, ratio, -pick_score, node))
    bottom_zone = screen_h > 0 and y >= int(screen_h * 0.72)
    nearby_radius = 110 if bottom_zone else 48
    if not hits:
        nearby = _nearby_identifiable_node(root, x, y, radius=nearby_radius)
        return _promote_labeled_target(root, nearby, x, y)

    hits.sort(key=lambda item: (item[0], item[1], item[2]))
    min_area = hits[0][0]
    # 面积帽收紧：只允许略大于最小命中的节点参与竞争（约 2.2 倍）
    area_cap = max(int(min_area * 2.2), min_area + 1)
    # 大节点进入候选须具备真实标识，且分数要明显高于小节点
    candidates = []
    for area, ratio, neg_score, node in hits:
        score = -neg_score
        if area <= area_cap:
            candidates.append((area, ratio, neg_score, node))
            continue
        # 超大布局容器一律不进候选（即使有 content-desc）
        if ratio > 0.28 and _is_layout_container(node.get("class") or ""):
            continue
        label = _node_pick_label(node)
        rid = (node.get("resource-id") or "").strip()
        has_identity = bool(label) or (rid and not _is_generic_rid(rid))
        if has_identity and score >= 55 and ratio <= 0.28:
            candidates.append((area, ratio, neg_score, node))

    best_node: ET.Element | None = None
    best_score = -10**9
    best_area = 10**18
    best_key: tuple[int, int, int] | None = None
    for area, ratio, neg_score, node in candidates:
        score = -neg_score
        # 主键：更高分优先；次键：更小面积；再次：叶子控件
        adjusted = score - (25 if ratio > 0.15 else 0)
        key = (-adjusted, area, 0 if _is_control_leaf(node.get("class") or "") else 1)
        if best_key is not None and best_node is not None and best_area > 0 and area > best_area * 2.5:
            # 大节点必须比当前小节点明显更高分才能替换
            if score < best_score + 30:
                continue
        if best_key is None or key < best_key:
            best_key = key
            best_node = node
            best_area = area
            best_score = score

    if best_node is not None and best_score <= -120:
        nearby = _nearby_identifiable_node(root, x, y, radius=nearby_radius)
        if nearby is not None:
            best_node = nearby
    if best_node is None and hits:
        best_node = hits[0][3]
    promoted = _promote_labeled_target(root, best_node, x, y)
    tight = _shrink_to_tightest_hit(root, promoted, x, y, screen_w, screen_h) or promoted
    return _prefer_smallest_under_point(root, tight, x, y, screen_w, screen_h) or tight


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
        label = _node_own_label(node)
        if _is_tab_like_label(label):
            score += 40
        if score <= 0 and node.get("clickable") != "true":
            continue
        if _is_generic_rid(node.get("resource-id") or ""):
            continue
        key = (dist, -score)
        if key < best_key:
            best_key = key
            best = node
    return best


def dump_ui_with_retry(serial: str, attempts: int = 2) -> str:
    import time as _time
    last = ""
    for i in range(max(1, attempts)):
        # 阻塞重试时 force，优先 u2
        last = dump_ui_text(serial, prefer_u2=True, force=(i > 0))
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
            if tap_priority_active(serial):
                return
            import tempfile
            from pathlib import Path
            local = Path(tempfile.gettempdir()) / f"atp_ui_{serial}.xml"
            res = try_dump_ui_xml(serial, str(local), prefer_u2=True)
            if res.get("ok"):
                xml = Path(res["local_path"]).read_text(encoding="utf-8", errors="ignore")
                if xml:
                    with _ui_dump_lock:
                        _ui_dump_cache[serial] = (time.time(), xml)
        finally:
            with _ui_dump_lock:
                _refresh_inflight.discard(serial)

    threading.Thread(target=job, daemon=True).start()


def dump_ui_cached(serial: str, *, allow_stale: bool = True, blocking: bool = True) -> str:
    """优先读缓存 UI 树，避免每次点击都同步 dump。"""
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
    if tap_priority_active(serial):
        if cached and allow_stale:
            return cached[1]
        time.sleep(0.35)
    xml = dump_ui_text(serial, prefer_u2=True, force=blocking)
    if xml:
        with _ui_dump_lock:
            _ui_dump_cache[serial] = (time.time(), xml)
    _schedule_ui_refresh(serial)
    return xml


def warm_ui_cache(serial: str, blocking: bool = False) -> dict:
    """预热 UI 缓存；blocking=True 时同步 dump 并写入缓存（优先 u2 hierarchy）。

    若缓存仍新鲜则直接复用，避免每次拾取都 invalidate + dump
    （dump 会导致部分机型画面闪烁，前端表现为“刷新页面”）。
    """
    from app_profile_helper import detect_app_profile

    def _with_profile(payload: dict, xml_text: str = "") -> dict:
        meta = get_last_dump_meta(serial)
        if meta.get("source") and "dump_source" not in payload:
            payload["dump_source"] = meta.get("source")
        try:
            profile = detect_app_profile(serial, xml_text)
            payload["app_profile"] = profile
            payload["page_context"] = payload.get("page_context") or (
                "webview" if profile.get("app_type") == "webview"
                else "hybrid" if profile.get("app_type") == "hybrid"
                else "native"
            )
            payload["needs_context_switch"] = profile.get("app_type") in ("webview", "hybrid")
        except Exception:
            pass
        return payload

    if blocking:
        with _ui_dump_lock:
            cached = _ui_dump_cache.get(serial)
        if cached and time.time() - cached[0] < CACHE_TTL_FRESH:
            ctx = detect_context_from_xml(cached[1])
            return _with_profile({
                "ok": True, "bytes": len(cached[1]), "cached": True, "blocking": True,
                "page_context": ctx, "needs_context_switch": ctx in ("webview", "hybrid"),
                "reused_cache": True,
                "dump_source": get_last_dump_meta(serial).get("source") or "cache",
            }, cached[1])
        invalidate_ui_cache(serial)
        res = dump_ui_xml(serial, prefer_u2=True, force=True, timeout=16)
        xml = ""
        if res.get("ok"):
            try:
                from pathlib import Path
                xml = Path(res["local_path"]).read_text(encoding="utf-8", errors="ignore")
            except Exception:
                xml = ""
        if xml:
            with _ui_dump_lock:
                _ui_dump_cache[serial] = (time.time(), xml)
            ctx = detect_context_from_xml(xml)
            return _with_profile({
                "ok": True, "bytes": len(xml), "cached": True, "blocking": True,
                "page_context": ctx, "needs_context_switch": ctx in ("webview", "hybrid"),
                "dump_source": res.get("source"),
            }, xml)
        return _with_profile({
            "ok": False,
            "error": res.get("error") or "ui_dump_failed",
            "blocking": True,
            "dump_source": res.get("source") or "fail",
        })
    with _ui_dump_lock:
        cached = _ui_dump_cache.get(serial)
    if cached and time.time() - cached[0] < CACHE_TTL_STALE:
        _schedule_ui_refresh(serial)
        return _with_profile({
            "ok": True, "bytes": len(cached[1]), "cached": True,
            "dump_source": get_last_dump_meta(serial).get("source") or "cache",
        }, cached[1])
    _schedule_ui_refresh(serial, force=True)
    return _with_profile({
        "ok": True, "warming": True, "cached": bool(cached),
        "dump_source": get_last_dump_meta(serial).get("source") or "",
    }, cached[1] if cached else "")


def invalidate_ui_cache(serial: str) -> None:
    with _ui_dump_lock:
        _ui_dump_cache.pop(serial, None)


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
    "xpath_desc_contains",
    "text",
    "xpath_text",
    "relative_xpath",
    "parent_index",
    "anchor_adjacent",
    "region_locator",
    "class_name",
    "absolute_xpath",
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
    matches = [node for node in root.iter("node") if _locator_exists_on_node(node, key, str(value))]
    if key in ("content_desc", "text", "xpath_desc") and len(matches) > 1:
        matches = _collapse_nested_attr_matches(matches)
    return len(matches)


def _collapse_nested_attr_matches(nodes: list[ET.Element]) -> list[ET.Element]:
    """同一属性挂在父子节点上时（常见 ImageView + 父容器同 content-desc），按一组计，避免误报重复。"""
    parsed: list[tuple[ET.Element, tuple[int, int, int, int]]] = []
    for n in nodes:
        b = _parse_bounds(n.get("bounds") or "")
        if b:
            parsed.append((n, b))
    if len(parsed) <= 1:
        return [n for n, _ in parsed] or nodes

    keep: list[ET.Element] = []
    for i, (ni, bi) in enumerate(parsed):
        nested_in_other = False
        for j, (nj, bj) in enumerate(parsed):
            if i == j:
                continue
            # bi 完全落在 bj 内，且 bj 明显更大 → ni 是子节点，从重复计数中去掉子节点
            if bi[0] >= bj[0] and bi[1] >= bj[1] and bi[2] <= bj[2] and bi[3] <= bj[3]:
                area_i = max(1, (bi[2] - bi[0]) * (bi[3] - bi[1]))
                area_j = max(1, (bj[2] - bj[0]) * (bj[3] - bj[1]))
                if area_j > area_i * 1.05:
                    nested_in_other = True
                    break
        if not nested_in_other:
            keep.append(ni)
    return keep or [n for n, _ in parsed]


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
        # 唯一性 / XPath 等值：短名不应把「短名+后缀」算作同一匹配
        return bool(desc and desc == val)
    if key == "xpath_desc_contains":
        needle = _xpath_needle(val)
        desc = (node.get("content-desc") or "").strip()
        return bool(needle and desc and needle in desc)
    if key == "class_name":
        clazz = (node.get("class") or "").strip()
        return bool(clazz and (val in clazz or clazz.endswith("." + val) or clazz == val))
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


def export_ui_hierarchy(serial: str, *, force: bool = False) -> dict:
    """导出当前 UI dump 为前端可浏览 JSON 树。"""
    from hierarchy_export import xml_to_hierarchy_tree

    if force:
        invalidate_ui_cache(serial)
        xml = dump_ui_cached(serial, allow_stale=False, blocking=True) or dump_ui(serial)
    else:
        xml = dump_ui_cached(serial, allow_stale=True, blocking=False)
        if not xml:
            xml = dump_ui_cached(serial, allow_stale=False, blocking=True) or dump_ui(serial)
    if not xml:
        return {"ok": False, "error": "ui_dump_failed", "root": None, "nodeCount": 0}
    tree = xml_to_hierarchy_tree(xml)
    meta = get_last_dump_meta(serial)
    tree["dump_source"] = meta.get("source") or tree.get("dump_source") or "cache"
    tree["serial_number"] = serial
    return tree


def inspect_by_bounds(serial: str, bounds: str, platform: str = "android") -> dict:
    """按 bounds 命中节点并生成与点选一致的定位链（供 UI 树点选）。"""
    xml = dump_ui_cached(serial, allow_stale=True, blocking=False) or dump_ui_cached(
        serial, allow_stale=False, blocking=True
    ) or dump_ui(serial)
    if not xml:
        return {"valid": False, "inspect_error": "ui_dump_failed", "bounds": bounds or ""}
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return {"valid": False, "inspect_error": "xml_parse_failed", "bounds": bounds or ""}

    target = str(bounds or "").strip()
    best_node = None
    for node in root.iter("node"):
        if (node.get("bounds") or "").strip() == target:
            best_node = node
            break
    if best_node is None:
        return {"valid": False, "inspect_error": "no_node_for_bounds", "bounds": target}

    parsed = _parse_bounds(target)
    cx = cy = 0
    if parsed:
        cx = (parsed[0] + parsed[2]) // 2
        cy = (parsed[1] + parsed[3]) // 2

    ui_w, ui_h = _hierarchy_size(root)
    text = (best_node.get("text") or "").strip()
    desc = (best_node.get("content-desc") or "").strip()
    rid = (best_node.get("resource-id") or "").strip()
    clazz = (best_node.get("class") or "").strip()
    clickable = best_node.get("clickable") == "true"
    name = _element_name(text, desc, rid, clazz)
    loc_type, loc_val = _locator_from_node(text, desc, rid)
    locators = _build_locators(best_node, root, text, desc, rid, clazz)
    if desc and not locators.get("xpath_desc_contains"):
        esc = desc.replace('"', '\\"')
        short_cls = clazz.split(".")[-1] if clazz else "*"
        locators["xpath_desc_contains"] = f'//{short_cls}[contains(@content-desc,"{esc}")]'
    if not loc_val and locators:
        if locators.get("id"):
            loc_type, loc_val = "id", str(locators["id"])
        elif locators.get("content_desc"):
            loc_type, loc_val = "content_desc", str(locators["content_desc"])
        elif locators.get("xpath_desc"):
            loc_type, loc_val = "xpath", str(locators["xpath_desc"])
    widget_type = _infer_widget_type(best_node, root)
    recognized = bool(name or loc_val or locators) and not _is_generic_rid(rid)
    result: dict = {
        "x": cx,
        "y": cy,
        "inspect_x": cx,
        "inspect_y": cy,
        "ui_width": ui_w,
        "ui_height": ui_h,
        "platform": platform or "android",
        "source": "uiautomator",
        "element_name": name,
        "display_name": _display_name(text, desc, rid),
        "locator_type": loc_type,
        "locator_value": loc_val,
        "locators": locators,
        "text": text or desc,
        "content_desc": desc,
        "resource_id": rid,
        "package": (best_node.get("package") or "").strip(),
        "index": best_node.get("index"),
        "class": clazz,
        "bounds": target,
        "clickable": clickable,
        "valid": recognized,
        "widget_type": widget_type,
        "suggested_step_type": _map_widget_to_step_type(widget_type),
        "tree_select": True,
    }
    return _enrich_inspect_metadata(root, best_node, result)


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

    # 阻塞拾取：强制新鲜 u2 dump，减少过期缓存误命中
    if blocking:
        invalidate_ui_cache(serial)
        xml = dump_ui_with_retry(serial, attempts=2)
        if xml:
            with _ui_dump_lock:
                _ui_dump_cache[serial] = (time.time(), xml)
    else:
        xml = dump_ui_cached(serial, allow_stale=True, blocking=False)
        # 非阻塞且缓存空：短暂等待后台 refresh 完成，避免录制连点全部 cache_miss
        if not xml and not tap_priority_active(serial):
            for _ in range(10):
                time.sleep(0.05)
                with _ui_dump_lock:
                    cached = _ui_dump_cache.get(serial)
                if cached and cached[1]:
                    xml = cached[1]
                    break
                if tap_priority_active(serial):
                    break

    dump_meta = get_last_dump_meta(serial)
    result["dump_source"] = dump_meta.get("source") or ("cache" if xml else "fail")
    result["strategy_used"] = "ui_tree"

    profile = None
    apply_strategy_to_inspect = None
    try:
        from app_profile_helper import detect_app_profile, apply_strategy_to_inspect as _apply_strategy
        profile = detect_app_profile(serial, xml or "")
        apply_strategy_to_inspect = _apply_strategy
    except Exception:
        profile = None
        apply_strategy_to_inspect = None

    if not xml:
        if not blocking:
            # 录制非阻塞拾取：禁止 force dump，避免抢占 input tap
            if not tap_priority_active(serial):
                _schedule_ui_refresh(serial, force=False)
            result["inspect_error"] = "cache_miss"
            result["strategy_used"] = "pending_refresh"
            result = _ensure_ratio_fallback(serial, x, y, display_width, display_height, result)
            if profile and apply_strategy_to_inspect:
                return apply_strategy_to_inspect(profile, result)
            return result
        result["inspect_error"] = "ui_dump_failed"
        # 自绘/游戏类或 dump 失败：直接走 OCR
        if profile and profile.get("recommended_strategy") in ("ocr", "coordinate"):
            ocr = _ocr_screen_fallback(serial, x, y, result)
            ocr["strategy_used"] = "ocr" if ocr.get("valid") else "coordinate"
            ocr = _ensure_ratio_fallback(serial, x, y, display_width, display_height, ocr)
            if profile and apply_strategy_to_inspect:
                return apply_strategy_to_inspect(profile, ocr)
            return ocr
        out = _ocr_screen_fallback(serial, x, y, result)
        out["strategy_used"] = "ocr" if out.get("valid") else "coordinate"
        out = _ensure_ratio_fallback(serial, x, y, display_width, display_height, out)
        if profile and apply_strategy_to_inspect:
            return apply_strategy_to_inspect(profile, out)
        return out

    # 明确不适合 UI 树时：先 OCR，UI 树仅作补充（仅阻塞模式；录制轻量路径跳过 OCR/截图）
    if blocking and profile and not profile.get("ui_tree_suitable") and profile.get("recommended_strategy") == "ocr":
        ocr = _ocr_screen_fallback(serial, x, y, dict(result))
        if ocr.get("valid"):
            ocr["source"] = ocr.get("source") or "ocr_screen"
            ocr["strategy_applied"] = "ocr_first"
            ocr["strategy_used"] = "ocr"
            ocr["dump_source"] = result.get("dump_source")
            if apply_strategy_to_inspect:
                return apply_strategy_to_inspect(profile, ocr)
            return ocr
        # OCR 未命中再尝试 UI 树

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

    # 自动识别 WebView 上下文并切换后重试拾取（仅阻塞模式，录制中禁止额外 dump）
    if blocking:
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
        if not blocking:
            # 录制轻量路径：至少写入比例坐标，避免步骤完全「未识别」
            result = _ensure_ratio_fallback(serial, x, y, display_width, display_height, result)
            if profile and apply_strategy_to_inspect:
                return apply_strategy_to_inspect(profile, result)
            return result
        ocr = _ocr_screen_fallback(serial, result.get("inspect_x", x), result.get("inspect_y", y), result)
        if profile and apply_strategy_to_inspect:
            return apply_strategy_to_inspect(profile, ocr)
        return ocr

    # 非阻塞命中弱结果时也给比例兜底，便于前端判定为已定位
    if not blocking and (not result.get("valid") or _is_weak_inspect_result(result)):
        result = _ensure_ratio_fallback(serial, x, y, display_width, display_height, result)

    if blocking and _is_weak_inspect_result(result):
        # 仅在缓存偏旧时才强制重 dump，避免每次弱命中都闪屏
        need_fresh = True
        with _ui_dump_lock:
            cached = _ui_dump_cache.get(serial)
            if cached and time.time() - cached[0] < CACHE_TTL_FRESH:
                need_fresh = False
        if need_fresh:
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

    if blocking and _is_weak_inspect_result(result) and not result.get("inspect_error"):
        ocr = _ocr_screen_fallback(serial, result.get("inspect_x", x), result.get("inspect_y", y), dict(result))
        if ocr.get("valid") and not _is_weak_inspect_result(ocr):
            ocr["strategy_used"] = "ocr"
            ocr["dump_source"] = result.get("dump_source")
            if profile and apply_strategy_to_inspect:
                return apply_strategy_to_inspect(profile, ocr)
            return ocr
        # OCR 也偏大/失败时：若仍有更紧 OCR 框则采用；否则压成点击焦点框，避免整页高亮
        if ocr.get("bounds") and not _is_weak_inspect_result({**result, **ocr, "valid": True}):
            result.update(ocr)
            result["strategy_used"] = "ocr"
        else:
            result["valid"] = False
            result["inspect_error"] = result.get("inspect_error") or "generic_container"
            result = _clamp_oversized_bounds(result, result.get("inspect_x", x), result.get("inspect_y", y))

    # 弱树 / 失败：保证至少有屏幕比例坐标兜底，避免空结果
    if blocking and (not result.get("valid") or _is_weak_inspect_result(result)):
        result = _ensure_ratio_fallback(serial, x, y, display_width, display_height, result)
        result = _clamp_oversized_bounds(result, result.get("inspect_x", x), result.get("inspect_y", y))
        if result.get("source") in ("ocr_screen", "ui_near_text"):
            result["strategy_used"] = result.get("strategy_used") or "ocr"
        elif result.get("locators", {}).get("screen_ratio") and not result.get("valid"):
            result["strategy_used"] = "coordinate"
            result.setdefault("risk_tags", [])
            if "ratio_fallback" not in result["risk_tags"]:
                result["risk_tags"] = list(result["risk_tags"]) + ["ratio_fallback"]

    if profile and apply_strategy_to_inspect:
        return apply_strategy_to_inspect(profile, result)
    return result


def _clamp_oversized_bounds(result: dict, x: int, y: int) -> dict:
    """命中整页级 bounds 时，改为点击点附近小框，避免前端高亮整屏。
    OCR / 近点文本已带合理 bounds 时不裁剪。
    """
    if result.get("source") in ("ocr_screen", "ui_near_text", "ocr_near") and result.get("bounds"):
        # OCR 行框通常远小于整页，保留原文框
        parsed_ocr = _parse_bounds(str(result.get("bounds") or ""))
        ui_w = int(result.get("ui_width") or result.get("display_width") or 0)
        ui_h = int(result.get("ui_height") or result.get("display_height") or 0)
        if parsed_ocr and ui_w > 0 and ui_h > 0:
            x1, y1, x2, y2 = parsed_ocr
            ratio = (max(1, x2 - x1) * max(1, y2 - y1)) / float(ui_w * ui_h)
            if ratio <= 0.28:
                return result
    bounds = str(result.get("bounds") or "")
    parsed = _parse_bounds(bounds)
    ui_w = int(result.get("ui_width") or result.get("display_width") or 0)
    ui_h = int(result.get("ui_height") or result.get("display_height") or 0)
    if not parsed or ui_w <= 0 or ui_h <= 0:
        return result
    x1, y1, x2, y2 = parsed
    w, h = max(1, x2 - x1), max(1, y2 - y1)
    ratio = (w * h) / float(ui_w * ui_h)
    if ratio <= 0.28:
        return result
    # 以点击点为中心裁约区域（按分辨率比例）
    half_w = max(48, int(ui_w * 0.08))
    half_h = max(48, int(ui_h * 0.045))
    cx = int(x if x is not None else (x1 + x2) // 2)
    cy = int(y if y is not None else (y1 + y2) // 2)
    nx1 = max(0, cx - half_w)
    ny1 = max(0, cy - half_h)
    nx2 = min(ui_w, cx + half_w)
    ny2 = min(ui_h, cy + half_h)
    result["bounds"] = f"[{nx1},{ny1}][{nx2},{ny2}]"
    result["bounds_clamped"] = True
    result.setdefault("risk_tags", [])
    if "oversized_bounds_clamped" not in result["risk_tags"]:
        result["risk_tags"] = list(result["risk_tags"]) + ["oversized_bounds_clamped"]
    return result


def _ensure_ratio_fallback(
    serial: str,
    x: int,
    y: int,
    display_width: int,
    display_height: int,
    result: dict,
) -> dict:
    """无可靠定位时写入屏幕比例坐标，便于用例至少可回放点击。"""
    locs = dict(result.get("locators") or {})
    if locs.get("screen_ratio"):
        return result
    w = int(display_width or result.get("ui_width") or 0)
    h = int(display_height or result.get("ui_height") or 0)
    if w <= 0 or h <= 0:
        w, h = _adb_display_size(serial)
    if w <= 0 or h <= 0:
        return result
    cx = max(0.0, min(1.0, float(x) / w))
    cy = max(0.0, min(1.0, float(y) / h))
    ratio = f"{cx:.4f},{cy:.4f}"
    locs["screen_ratio"] = ratio
    result["locators"] = locs
    if not result.get("locator_type") or result.get("locator_type") in ("", "bounds"):
        result["locator_type"] = "screen_ratio"
        result["locator_value"] = ratio
    if not result.get("element_name"):
        result["element_name"] = f"ratio_{int(cx * 1000)}_{int(cy * 1000)}"
    if not result.get("display_name"):
        result["display_name"] = f"坐标比例 {ratio}"
    result.setdefault("risk_level", "medium")
    reasons = list(result.get("risk_reasons") or [])
    if "已生成屏幕比例坐标兜底" not in reasons:
        reasons.append("已生成屏幕比例坐标兜底")
    result["risk_reasons"] = reasons
    return result


def _ocr_screen_fallback(serial: str, x: int, y: int, result: dict) -> dict:
    """无控件树/弱命中时：先 UI 树近点文本，再截图近点 OCR。"""
    near = _ocr_near_point(serial, x, y, dict(result))
    if near.get("valid") and near.get("text"):
        near["source"] = near.get("source") or "ui_near_text"
        return near
    try:
        from ocr_helper import ocr_pick_at_point
        pick = ocr_pick_at_point(serial, x, y)
    except Exception:
        return near if near.get("text") else result
    if not pick.get("text"):
        return near if near.get("text") else result
    text = str(pick["text"]).strip()
    locs = {"ocr": text, "text": text}
    bounds = str(pick.get("bounds") or "").strip()
    iw = int(pick.get("image_width") or 0)
    ih = int(pick.get("image_height") or 0)
    tw = int(result.get("ui_width") or result.get("display_width") or 0)
    th = int(result.get("ui_height") or result.get("display_height") or 0)
    if bounds and iw > 0 and ih > 0 and tw > 0 and th > 0 and (abs(iw - tw) > 8 or abs(ih - th) > 8):
        parsed = _parse_bounds(bounds)
        if parsed:
            x1, y1, x2, y2 = parsed
            x1 = int(round(x1 * tw / iw))
            y1 = int(round(y1 * th / ih))
            x2 = int(round(x2 * tw / iw))
            y2 = int(round(y2 * th / ih))
            bounds = f"[{x1},{y1}][{x2},{y2}]"
    if bounds:
        locs["bounds"] = bounds
    result.update({
        "source": "ocr_screen",
        "element_name": _element_name(text, "", ""),
        "display_name": text,
        "text": text,
        "locator_type": "ocr",
        "locator_value": text,
        "locators": locs,
        "valid": True,
        "suggested_step_type": "tap_ocr",
        "widget_type": "text",
        "risk_level": "medium",
        "risk_tags": ["ocr_fallback"],
    })
    if bounds:
        result["bounds"] = bounds
    return result


def _ocr_near_point(serial: str, x: int, y: int, result: dict) -> dict:
    """从 UI dump 找点击附近短文本（底栏「定时/教程」优先）。"""
    # 禁止 fallback 到 blocking dump，避免录制点击被卡住
    xml = dump_ui_cached(serial, allow_stale=True, blocking=False)
    if not xml:
        return result
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return result
    screen_w, screen_h = _hierarchy_size(root)
    bottom_zone = screen_h > 0 and y >= int(screen_h * 0.72)
    max_dist = 180 if bottom_zone else 120
    best_text = ""
    best_node = None
    best_key = (10**9, 1)
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        parsed = _parse_bounds(bounds)
        if not parsed:
            continue
        x1, y1, x2, y2 = parsed
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if x1 <= x <= x2 and y1 <= y <= y2:
            dist = abs(cx - x) + abs(cy - y) // 4
        else:
            dist = abs(cx - x) + abs(cy - y)
        if dist > max_dist:
            continue
        text = _node_own_label(node)
        if len(text) < 1:
            continue
        # 短标签优先
        tab_rank = 0 if _is_tab_like_label(text) else 1
        key = (dist, tab_rank)
        if key < best_key:
            best_key = key
            best_text = text
            best_node = node
    if best_text:
        locators = {"text": best_text, "ocr": best_text}
        if best_node is not None:
            desc = (best_node.get("content-desc") or "").strip()
            rid = (best_node.get("resource-id") or "").strip()
            if desc:
                locators["content_desc"] = desc
            if rid and not _is_generic_rid(rid):
                locators["id"] = rid.split(":id/")[-1] if ":id/" in rid else rid
            if best_node.get("bounds"):
                locators["bounds"] = best_node.get("bounds")
        primary = "content_desc" if locators.get("content_desc") == best_text else "text"
        result.update({
            "source": "ui_near_text",
            "element_name": _element_name(best_text, "", ""),
            "display_name": best_text,
            "text": best_text,
            "locator_type": primary,
            "locator_value": best_text,
            "locators": locators,
            "valid": True,
            "widget_type": "click",
            "suggested_step_type": "click",
            "class": (best_node.get("class") if best_node is not None else "") or "",
            "bounds": (best_node.get("bounds") if best_node is not None else "") or "",
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


_XPATH_SEG_RE = re.compile(r"^(\*|[A-Za-z_][\w.]*)(?:\[(\d+)\])?$")


def _parse_class_path_xpath(value: str) -> list[tuple[str, int]] | None:
    """解析 //A[1]/B[2] 或 /hierarchy/A[1]/B[2] 为 [(类名短名, 下标), ...]。"""
    path = str(value or "").strip()
    if not path:
        return None
    if path.startswith("/hierarchy/"):
        path = path[len("/hierarchy/"):]
    elif path.startswith("//"):
        path = path[2:]
    elif path.startswith("/"):
        path = path[1:]
    parts: list[tuple[str, int]] = []
    for seg in path.split("/"):
        seg = seg.strip()
        if not seg or seg == "hierarchy":
            continue
        m = _XPATH_SEG_RE.match(seg)
        if not m:
            return None
        raw = m.group(1) or ""
        if raw == "*":
            continue  # 旧数据中的 hierarchy 伪节点
        clazz = raw.split(".")[-1]
        idx = int(m.group(2) or 1)
        if not clazz or idx < 1:
            return None
        parts.append((clazz, idx))
    return parts or None


def _child_nodes(parent: ET.Element) -> list[ET.Element]:
    return [c for c in list(parent) if isinstance(c.tag, str)]


def _walk_class_path_from(parent: ET.Element, parts: list[tuple[str, int]]) -> ET.Element | None:
    """从 parent 的子节点起，按类名+下标链向下走。"""
    current_parent = parent
    current: ET.Element | None = None
    for clazz, idx in parts:
        candidates = _child_nodes(current_parent)
        want = clazz.lower()
        same = [n for n in candidates if _class_short(n.get("class") or "") == want]
        if len(same) < idx:
            return None
        current = same[idx - 1]
        current_parent = current
    return current


def _find_nodes_by_class_path(root: ET.Element, value: str) -> list[ET.Element]:
    """按类名+下标链在 UI 树中定位（相对/绝对 xpath）。

    - `/hierarchy/...`：从根向下绝对匹配
    - `//...`：相对路径，可能因深度截断不从根开始，需在整棵树中寻找可走通的起点
    """
    parts = _parse_class_path_xpath(value)
    if not parts:
        return []
    parts = [(c, i) for c, i in parts if c != "*"]
    if not parts:
        return []

    raw = str(value or "").strip()
    is_relative = raw.startswith("//")

    hits: list[ET.Element] = []
    seen: set[int] = set()

    if is_relative:
        # 每个节点都可作为「第一段」的父容器（含 hierarchy 根）
        starts: list[ET.Element] = [root]
        for n in root.iter():
            if n is not root:
                starts.append(n)
        for start in starts:
            hit = _walk_class_path_from(start, parts)
            if hit is None:
                continue
            hid = id(hit)
            if hid in seen:
                continue
            seen.add(hid)
            if _node_is_visible(hit, root):
                hits.append(hit)
        return hits

    # 绝对路径：仅从 hierarchy / 根走
    start = root
    hit = _walk_class_path_from(start, parts)
    if hit is not None and _node_is_visible(hit, root):
        return [hit]
    return []


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


def _root_screen_size(root: ET.Element) -> tuple[int, int]:
    """从 hierarchy 根节点 bounds 取屏幕宽高。"""
    for node in root.iter("node"):
        parsed = _parse_bounds(node.get("bounds") or "")
        if parsed and parsed[0] == 0 and parsed[1] == 0 and parsed[2] > 0 and parsed[3] > 0:
            return parsed[2], parsed[3]
    return 0, 0


def _nodes_at_point(root: ET.Element, x: int, y: int) -> list[ET.Element]:
    hits: list[ET.Element] = []
    for node in root.iter("node"):
        parsed = _parse_bounds(node.get("bounds") or "")
        if not parsed:
            continue
        x1, y1, x2, y2 = parsed
        if x1 <= x < x2 and y1 <= y < y2:
            hits.append(node)
    # 面积小的更可能是目标控件
    def _area(n: ET.Element) -> int:
        b = _parse_bounds(n.get("bounds") or "")
        if not b:
            return 10**12
        return max(1, (b[2] - b[0]) * (b[3] - b[1]))
    hits.sort(key=_area)
    return hits


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
    if key in ("relative_xpath", "absolute_xpath"):
        nodes = _find_nodes_by_class_path(root, val)
        if nodes:
            return nodes
    if key == "xpath_desc_contains":
        needle = _xpath_needle(val)
        if not needle:
            return []
        nodes = []
        for node in root.iter("node"):
            desc = (node.get("content-desc") or "").strip()
            if desc and needle in desc:
                nodes.append(node)
        return nodes
    if key in ("xpath_desc", "xpath_text"):
        needle = _xpath_needle(val)
        nodes = [n for n in root.iter("node") if _node_text_matches(n, needle)]
        if nodes:
            return nodes
    if key == "xpath_desc":
        val = _xpath_needle(val)
        key = "content_desc"
    if key == "xpath_text":
        val = _xpath_needle(val)
        key = "text"
    nodes: list[ET.Element] = []
    for node in root.iter("node"):
        if _locator_exists_on_node(node, key, val):
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
            result.update({"found": True, "reason": "not_visible", "bounds": val})
        return result
    if key == "screen_ratio":
        parts = [p.strip() for p in val.split(",")]
        if len(parts) < 2:
            result["reason"] = "invalid_ratio"
            return result
        try:
            rx = float(parts[0])
            ry = float(parts[1])
        except ValueError:
            result["reason"] = "invalid_ratio"
            return result
        ui_w, ui_h = _root_screen_size(root)
        if ui_w <= 0 or ui_h <= 0:
            result["reason"] = "no_screen_size"
            return result
        px = int(round(rx * ui_w))
        py = int(round(ry * ui_h))
        hits = _nodes_at_point(root, px, py)
        result["match_count"] = len(hits)
        if not hits:
            result["reason"] = "not_found"
            return result
        result["found"] = True
        tappable = [n for n in hits if _node_is_tappable(n, root)]
        visible = [n for n in hits if _node_is_visible(n, root)]
        pick = (tappable or visible or hits)[0]
        result["bounds"] = pick.get("bounds") or ""
        if tappable:
            result.update({"visible": True, "clickable": True})
        elif visible:
            result.update({"visible": True, "reason": "not_clickable"})
        else:
            result["reason"] = "not_visible"
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
