"""按 Android/iOS 选型规范评分并标记推荐定位（设计文档 §2 / §3）"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET

ANDROID_CHAIN_PRIORITY = (
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

IOS_CHAIN_PRIORITY = (
    "accessibility_id",
    "id",
    "nspredicate",
    "content_desc",
    "text",
    "class_name",
    "bounds",
    "screen_ratio",
    "ocr",
)

ANDROID_BASE_SCORE = {
    "id": 100,
    "resource_id": 100,
    "uiselector": 88,
    "content_desc": 78,
    "xpath_desc": 72,
    "text": 52,
    "xpath_text": 48,
    "relative_xpath": 46,
    "parent_index": 44,
    "anchor_adjacent": 42,
    "region_locator": 40,
    "class_name": 22,
    "bounds": 18,
    "screen_ratio": 12,
    "ocr": 8,
}

IOS_BASE_SCORE = {
    "accessibility_id": 100,
    "id": 98,
    "nspredicate": 86,
    "content_desc": 72,
    "text": 50,
    "class_name": 24,
    "bounds": 18,
    "screen_ratio": 12,
    "ocr": 8,
}

RECOMMEND_REASON = {
    "id": "唯一 resourceId / identifier",
    "resource_id": "唯一 resourceId",
    "accessibility_id": "iOS 自动化专属标识 accessibilityIdentifier",
    "uiselector": "Android UiSelector 原生组合定位",
    "nspredicate": "iOS NSPredicate 原生组合定位",
    "content_desc": "无障碍描述 content-desc / label",
    "xpath_desc": "基于 content-desc 的相对 XPath",
    "text": "静态固定文本",
    "xpath_text": "基于文本的相对 XPath",
    "relative_xpath": "短相对 XPath 备选",
    "parent_index": "父容器 + 下标（列表场景）",
    "anchor_adjacent": "锚点邻位相对定位",
    "region_locator": "区域限定定位",
    "class_name": "控件类名（稳定性偏低）",
    "bounds": "Bounds 坐标",
    "screen_ratio": "屏幕比例兜底",
    "ocr": "OCR 文本兜底",
}

_DYNAMIC_TEXT_HINTS = (
    re.compile(r"^\d{4,}$"),
    re.compile(r"验证码|captcha|code", re.I),
    re.compile(r"^\d{1,2}:\d{2}$"),
)

GENERIC_RIDS = frozenset({"content", "decor_content_parent", "action_bar_root"})


def is_generic_rid(rid: str) -> bool:
    short = str(rid or "").split(":id/")[-1].split("/")[-1].lower()
    return short in GENERIC_RIDS


def is_dynamic_text(text: str) -> bool:
    if not text or not str(text).strip():
        return True
    s = str(text).strip()
    for pat in _DYNAMIC_TEXT_HINTS:
        if pat.search(s):
            return True
    return False


def build_nspredicate(node: ET.Element) -> str:
    identifier = (node.get("name") or node.get("identifier") or "").strip()
    label = (node.get("label") or node.get("value") or "").strip()
    cls = (node.get("type") or node.get("class") or "").strip()
    cls_short = cls.split(".")[-1] if cls else ""
    parts: list[str] = []
    if identifier:
        esc = identifier.replace('"', '\\"')
        parts.append(f'name == "{esc}"')
    elif label and not is_dynamic_text(label):
        esc = label.replace('"', '\\"')
        parts.append(f'label == "{esc}"')
    elif cls_short:
        parts.append(f'type == "{cls_short}"')
    return " AND ".join(parts) if parts else ""


def _count_matches(root: ET.Element | None, key: str, value: str) -> int:
    if root is None or not value:
        return 0
    count = 0
    val = str(value).strip()
    for node in root.iter("node"):
        if key in ("id", "resource_id"):
            rid = (node.get("resource-id") or "").strip()
            short = val.split(":id/")[-1] if ":id/" in val else val
            if rid and (rid == val or rid.endswith("/" + short) or short in rid):
                count += 1
        elif key in ("text", "ocr"):
            text = (node.get("text") or "").strip()
            if text and (text == val or val in text):
                count += 1
        elif key in ("content_desc", "xpath_desc", "accessibility_id"):
            desc = (node.get("content-desc") or node.get("label") or node.get("name") or "").strip()
            if desc and (desc == val or val in desc):
                count += 1
    return count


def score_locator(
    key: str,
    value: str,
    platform: str = "android",
    root: ET.Element | None = None,
) -> int:
    if not value or not str(value).strip():
        return -999
    val = str(value).strip()
    if key in ("id", "resource_id") and is_generic_rid(val):
        return -999
    if key in ("text", "ocr") and is_dynamic_text(val):
        return 6
    if key == "class_name":
        base = 18 if platform == "android" else 20
    else:
        table = IOS_BASE_SCORE if platform == "ios" else ANDROID_BASE_SCORE
        base = table.get(key, 10)
    if root and key in ("id", "resource_id", "text", "content_desc", "accessibility_id"):
        cnt = _count_matches(root, key, val)
        if cnt > 1:
            base -= min(35, (cnt - 1) * 12)
        elif cnt == 1:
            base += 5
    if key in ("relative_xpath", "xpath_desc", "xpath_text") and len(val) > 120:
        base -= 15
    return base


def chain_priority(platform: str) -> tuple[str, ...]:
    return IOS_CHAIN_PRIORITY if platform == "ios" else ANDROID_CHAIN_PRIORITY


def build_recommended_chain(
    locators: dict,
    platform: str = "android",
    root: ET.Element | None = None,
) -> list[dict]:
    priority = chain_priority(platform)
    chain: list[dict] = []
    seen: set[str] = set()
    for idx, key in enumerate(priority):
        val = locators.get(key)
        if not val:
            continue
        if key == "absolute_xpath":
            continue
        if key in ("id", "resource_id") and is_generic_rid(str(val)):
            continue
        if key in ("text", "ocr") and is_dynamic_text(str(val)):
            continue
        sig = f"{key}:{val}"
        if sig in seen:
            continue
        seen.add(sig)
        chain.append({
            "type": key,
            "value": str(val),
            "enabled": True,
            "priority": idx + 1,
            "recommend_score": score_locator(key, str(val), platform, root),
        })

    if not chain:
        return []

    best_idx = max(range(len(chain)), key=lambda i: chain[i]["recommend_score"])
    best = chain[best_idx]
    reason = RECOMMEND_REASON.get(best["type"], "按平台规范推荐")
    for i, item in enumerate(chain):
        item["recommended"] = i == best_idx
        item["primary"] = i == best_idx
        if i == best_idx:
            item["recommend_reason"] = reason
    return chain


def pick_primary_from_locators(
    locators: dict,
    platform: str = "android",
    root: ET.Element | None = None,
) -> tuple[str, str]:
    chain = build_recommended_chain(locators, platform, root)
    if not chain:
        return "", ""
    best = chain[0]
    for item in chain:
        if item.get("recommended"):
            best = item
            break
    key = best["type"]
    mapped = "id" if key == "resource_id" else key
    if mapped in ("xpath_desc", "xpath_text", "relative_xpath", "parent_index", "anchor_adjacent", "region_locator"):
        mapped = "xpath"
    if key == "accessibility_id":
        mapped = "accessibility"
    if key == "nspredicate":
        mapped = "accessibility"
    return mapped, best["value"]
