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
    "xpath_desc_contains": 68,
    "text": 52,
    "xpath_text": 48,
    "relative_xpath": 46,
    "parent_index": 44,
    "anchor_adjacent": 42,
    "region_locator": 40,
    "class_name": 22,
    "absolute_xpath": 16,
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
    "content_desc": "文案定位（content-desc / label）",
    "xpath_desc": "xpath（基于 content-desc）",
    "xpath_desc_contains": "xpath（contains content-desc）",
    "text": "文本定位（短标签优先）",
    "xpath_text": "xpath（基于文本）",
    "relative_xpath": "相对 xpath",
    "absolute_xpath": "绝对 xpath（从根节点完整路径，页面结构变化时易失效）",
    "parent_index": "父容器下标（列表场景）",
    "anchor_adjacent": "锚点定位",
    "region_locator": "区域定位",
    "class_name": "类名定位（稳定性偏低）",
    "bounds": "坐标定位",
    "screen_ratio": "屏幕比例兜底",
    "ocr": "OCR 定位兜底",
}

_DYNAMIC_TEXT_HINTS = (
    re.compile(r"^\d{4,}$"),
    re.compile(r"验证码|captcha|code", re.I),
    re.compile(r"^\d{1,2}:\d{2}$"),
)

# 营销长描述 / 说明文案：不宜作为首选定位
_LONG_COPY_HINTS = (
    re.compile(r"[，。；、：]|日常|期间|自动|勿扰|了解更多|点击|请|将"),
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


def _is_short_option_label(val: str) -> bool:
    s = str(val or "").strip()
    if not s:
        return False
    if re.fullmatch(r"\d{1,2}", s):
        return True
    if 1 <= len(s) <= 6 and re.fullmatch(r"[\u4e00-\u9fffA-Za-z0-9]+", s):
        return True
    return False


def _is_long_marketing_copy(val: str) -> bool:
    s = str(val or "").strip()
    if len(s) >= 16:
        return True
    if len(s) >= 8 and any(p.search(s) for p in _LONG_COPY_HINTS):
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
    elif label and not is_dynamic_text(label) and not _is_long_marketing_copy(label):
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
            if rid and (rid == val or rid.endswith("/" + short) or rid.endswith(":id/" + short)):
                count += 1
        elif key in ("text", "ocr"):
            text = (node.get("text") or "").strip()
            # 精确匹配，避免长文案互相包含导致误判
            if text and text == val:
                count += 1
        elif key in ("content_desc", "xpath_desc", "accessibility_id"):
            desc = (node.get("content-desc") or node.get("label") or node.get("name") or "").strip()
            if desc and desc == val:
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

    # 长说明/营销文案大幅降权，避免「内容描述定位」总被推荐为大段文字
    if key in ("content_desc", "xpath_desc", "text", "xpath_text", "ocr"):
        if _is_long_marketing_copy(val):
            base -= 55 if key.startswith("content") or key == "xpath_desc" else 40
        elif _is_short_option_label(val):
            base += 18
        elif len(val) > 10:
            base -= min(30, (len(val) - 10) * 2)

    if root and key in ("id", "resource_id", "text", "content_desc", "accessibility_id"):
        cnt = _count_matches(root, key, val)
        if cnt > 1:
            base -= min(45, (cnt - 1) * 14)
        elif cnt == 1:
            base += 8
        elif cnt == 0:
            # dump 中未校验到唯一性时略降，避免虚高
            base -= 3
    if key in ("relative_xpath", "xpath_desc", "xpath_text", "absolute_xpath") and len(val) > 120:
        base -= 15
    # 有相对路径且文案很长时，相对路径更稳
    if key == "relative_xpath" and len(val) <= 80:
        base += 6
    if key == "absolute_xpath":
        base -= 4  # 绝对路径脆，默认不当作推荐首选
    return base


def score_to_pass_rate(score: int | float) -> int:
    """将推荐分映射为预估通过率（%），供前端优先级排序展示。"""
    try:
        s = float(score)
    except (TypeError, ValueError):
        return 5
    if s <= -100:
        return 5
    # 基准分约 100 → ~95%，50 → ~68%，0 → ~40%，负分更低
    rate = int(round(40 + s * 0.55))
    return max(5, min(99, rate))


def chain_priority(platform: str) -> tuple[str, ...]:
    return IOS_CHAIN_PRIORITY if platform == "ios" else ANDROID_CHAIN_PRIORITY


def _recommend_reason_for(key: str, value: str, has_id: bool) -> str:
    if key in ("content_desc", "xpath_desc", "text", "xpath_text"):
        if _is_long_marketing_copy(value):
            return "当前文案过长（说明/营销描述），稳定性偏低，建议改用 id 或短标签备选"
        if not has_id:
            return RECOMMEND_REASON.get(key, "") + "；控件无稳定 resource-id，故推荐文案类定位"
    return RECOMMEND_REASON.get(key, "按平台规范推荐")


def build_recommended_chain(
    locators: dict,
    platform: str = "android",
    root: ET.Element | None = None,
) -> list[dict]:
    priority = chain_priority(platform)
    chain: list[dict] = []
    seen: set[str] = set()
    has_id = bool(
        (locators.get("id") or locators.get("resource_id"))
        and not is_generic_rid(str(locators.get("id") or locators.get("resource_id") or ""))
    )
    for key in priority:
        val = locators.get(key)
        if not val:
            continue
        if key in ("id", "resource_id") and is_generic_rid(str(val)):
            continue
        if key in ("text", "ocr") and is_dynamic_text(str(val)):
            continue
        # 超长 content-desc 仍可进链作备选，但评分会很低
        sig = f"{key}:{val}"
        if sig in seen:
            continue
        seen.add(sig)
        score = score_locator(key, str(val), platform, root)
        chain.append({
            "type": key,
            "value": str(val),
            "enabled": True,
            "recommend_score": score,
            "pass_rate": score_to_pass_rate(score),
        })

    if not chain:
        return []

    # 按预估通过率（推荐分）降序，优先级 1 = 最稳
    chain.sort(key=lambda x: (x.get("recommend_score") or -999, x.get("pass_rate") or 0), reverse=True)
    best = chain[0]
    reason = _recommend_reason_for(best["type"], best["value"], has_id)
    for i, item in enumerate(chain):
        item["priority"] = i + 1
        item["pass_rate"] = int(item.get("pass_rate") or score_to_pass_rate(item.get("recommend_score") or 0))
        item["recommended"] = i == 0
        item["primary"] = i == 0
        if i == 0:
            item["recommend_reason"] = reason
        else:
            item.pop("recommend_reason", None)
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
    if mapped in ("xpath_desc", "xpath_desc_contains", "xpath_text", "relative_xpath", "absolute_xpath", "parent_index", "anchor_adjacent", "region_locator"):
        mapped = "xpath"
    if key == "accessibility_id":
        mapped = "accessibility"
    if key == "nspredicate":
        mapped = "accessibility"
    return mapped, best["value"]
