"""当前前台 App 类型检测，并给出控件拾取策略建议。"""

from __future__ import annotations

import re
import subprocess
import xml.etree.ElementTree as ET
from typing import Any


FLUTTER_MARKERS = (
    "io.flutter.embedding",
    "io.flutter.view",
    "flutterview",
    "flutter.view",
)
RN_MARKERS = (
    "com.facebook.react",
    "reactrootview",
    "reactnative",
)
COMPOSE_MARKERS = (
    "androidx.compose",
    "androidcomposeview",
    "composeview",
)
GAME_MARKERS = (
    "unityplayer",
    "com.unity3d",
    "cocos2dx",
    "unreal",
    "libgdx",
)
WEBVIEW_MARKERS = (
    "android.webkit.webview",
    "com.android.webview",
)
SURFACE_MARKERS = (
    "surfaceview",
    "glsurfaceview",
    "textureview",
    "android.view.surfaceview",
)

# 常见原生控件 class，用于统计 UI 树可识别度
NATIVE_WIDGET_HINTS = (
    "button", "textview", "edittext", "imagebutton", "imageview",
    "checkbox", "radiobutton", "switch", "recyclerview", "listview",
    "toolbar", "actionbar", "bottomnavigation", "tablayout",
)


def _adb_shell(serial: str, *args: str, timeout: float = 6) -> str:
    try:
        r = subprocess.run(
            ["adb", "-s", serial, "shell", *args],
            capture_output=True, text=True, timeout=timeout, encoding="utf-8", errors="ignore",
        )
        return (r.stdout or "") + (r.stderr or "")
    except Exception:
        return ""


def get_foreground_package(serial: str) -> str:
    """读取当前前台包名。"""
    text = _adb_shell(serial, "dumpsys", "window", "windows")
    for pat in (
        r"mCurrentFocus=Window\{[^ ]+ ([^/}\s]+)/",
        r"mFocusedApp=.*? ([^/}\s]+)/",
        r"topResumedActivity=.*? ([^/}\s]+)/",
    ):
        m = re.search(pat, text)
        if m:
            pkg = m.group(1).strip()
            if pkg and pkg not in ("null", "StatusBar"):
                return pkg
    text2 = _adb_shell(serial, "dumpsys", "activity", "activities")
    m = re.search(r"mResumedActivity:.*? ([^/\s]+)/", text2)
    if m:
        return m.group(1).strip()
    return ""


def _parse_bounds(bounds: str) -> tuple[int, int, int, int] | None:
    nums = [int(n) for n in re.findall(r"\d+", bounds or "")]
    if len(nums) >= 4:
        return nums[0], nums[1], nums[2], nums[3]
    return None


def _xml_stats(xml: str) -> dict[str, Any]:
    stats = {
        "node_count": 0,
        "with_id": 0,
        "with_text": 0,
        "with_desc": 0,
        "clickable": 0,
        "native_widgets": 0,
        "surface_views": 0,
        "webview_nodes": 0,
        "flutter_nodes": 0,
        "rn_nodes": 0,
        "compose_nodes": 0,
        "game_nodes": 0,
        "large_view_ratio": 0.0,
        "identifiable_ratio": 0.0,
    }
    if not xml:
        return stats
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return stats

    max_x = max_y = 1
    large_views = 0
    for node in root.iter("node"):
        stats["node_count"] += 1
        clazz = (node.get("class") or "").lower()
        rid = (node.get("resource-id") or "").strip()
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        if rid:
            stats["with_id"] += 1
        if text:
            stats["with_text"] += 1
        if desc:
            stats["with_desc"] += 1
        if node.get("clickable") == "true":
            stats["clickable"] += 1
        if any(h in clazz for h in NATIVE_WIDGET_HINTS):
            stats["native_widgets"] += 1
        if any(m in clazz for m in SURFACE_MARKERS):
            stats["surface_views"] += 1
        if any(m in clazz for m in WEBVIEW_MARKERS) or "webview" in clazz:
            stats["webview_nodes"] += 1
        if any(m in clazz for m in FLUTTER_MARKERS):
            stats["flutter_nodes"] += 1
        if any(m in clazz for m in RN_MARKERS):
            stats["rn_nodes"] += 1
        if any(m in clazz for m in COMPOSE_MARKERS):
            stats["compose_nodes"] += 1
        if any(m in clazz for m in GAME_MARKERS):
            stats["game_nodes"] += 1
        parsed = _parse_bounds(node.get("bounds") or "")
        if parsed:
            max_x = max(max_x, parsed[2])
            max_y = max(max_y, parsed[3])
            area = (parsed[2] - parsed[0]) * (parsed[3] - parsed[1])
            if clazz.endswith(".view") or clazz == "android.view.view":
                if area > max_x * max_y * 0.35:
                    large_views += 1

    n = max(stats["node_count"], 1)
    stats["large_view_ratio"] = round(large_views / n, 3)
    identifiable = stats["with_id"] + stats["with_text"] + stats["with_desc"]
    stats["identifiable_ratio"] = round(identifiable / n, 3)
    return stats


def _package_signals(serial: str, package: str) -> list[str]:
    signals: list[str] = []
    if not package:
        return signals
    paths = _adb_shell(serial, "pm", "path", package)
    # dumpsys package 查依赖特征（轻量）
    dump = _adb_shell(serial, "dumpsys", "package", package, timeout=8)
    lower = (dump + "\n" + paths).lower()
    if "io.flutter" in lower or "flutter" in lower:
        signals.append("package_has_flutter")
    if "com.facebook.react" in lower or "reactnative" in lower:
        signals.append("package_has_react_native")
    if "androidx.compose" in lower:
        signals.append("package_has_compose")
    if "unity3d" in lower or "libunity" in lower:
        signals.append("package_has_unity")
    if "cocos" in lower:
        signals.append("package_has_cocos")
    return signals


def detect_app_profile(serial: str, xml: str = "", package: str = "") -> dict[str, Any]:
    """
    判断当前 App 是否适合用 UI 树拾取，并给出推荐策略。

    app_type:
      native | compose | react_native | flutter | webview | hybrid |
      game | canvas_custom | unknown
    """
    pkg = package or get_foreground_package(serial)
    stats = _xml_stats(xml)
    pkg_signals = _package_signals(serial, pkg) if serial else []
    signals = list(pkg_signals)

    lower_xml = (xml or "").lower()
    if any(m in lower_xml for m in FLUTTER_MARKERS) or stats["flutter_nodes"] > 0:
        signals.append("xml_flutter_view")
    if any(m in lower_xml for m in RN_MARKERS) or stats["rn_nodes"] > 0:
        signals.append("xml_react_root")
    if any(m in lower_xml for m in COMPOSE_MARKERS) or stats["compose_nodes"] > 0:
        signals.append("xml_compose")
    if any(m in lower_xml for m in GAME_MARKERS) or stats["game_nodes"] > 0:
        signals.append("xml_game_engine")
    if stats["webview_nodes"] > 0 or "android.webkit.webview" in lower_xml:
        signals.append("xml_webview")
    if stats["surface_views"] > 0:
        signals.append("xml_surface_view")

    app_type = "native"
    if "xml_game_engine" in signals or "package_has_unity" in signals or "package_has_cocos" in signals:
        app_type = "game"
    elif "xml_flutter_view" in signals or "package_has_flutter" in signals:
        app_type = "flutter"
    elif "xml_react_root" in signals or "package_has_react_native" in signals:
        app_type = "react_native"
    elif "xml_webview" in signals and stats["identifiable_ratio"] < 0.15:
        app_type = "webview"
    elif "xml_webview" in signals:
        app_type = "hybrid"
    elif "xml_compose" in signals or "package_has_compose" in signals:
        app_type = "compose"
    elif (
        stats["surface_views"] > 0
        and stats["identifiable_ratio"] < 0.12
        and stats["with_id"] < max(3, stats["node_count"] * 0.05)
    ):
        app_type = "canvas_custom"
        signals.append("few_a11y_ids_with_surface")
    elif (
        stats["node_count"] > 0
        and stats["identifiable_ratio"] < 0.08
        and stats["large_view_ratio"] > 0.02
        and stats["native_widgets"] < 3
    ):
        app_type = "canvas_custom"
        signals.append("sparse_a11y_tree")

    # 策略
    if app_type in ("game", "canvas_custom"):
        ui_tree_suitable = False
        strategy = "ocr"
        reason = "当前界面以自绘/游戏层为主，无障碍树缺少稳定 id/文本，建议优先 OCR 或屏幕比例坐标"
    elif app_type == "flutter":
        # Flutter Semantics 有时可用，但多数业务页更适合 OCR
        ui_tree_suitable = stats["identifiable_ratio"] >= 0.12
        strategy = "hybrid" if ui_tree_suitable else "ocr"
        reason = (
            "检测到 Flutter：Semantics 可用时尝试 UI 树，否则推荐 OCR + 坐标兜底"
            if ui_tree_suitable
            else "检测到 Flutter 且无障碍信息稀疏，推荐 OCR / 坐标拾取"
        )
    elif app_type == "webview":
        ui_tree_suitable = True
        strategy = "hybrid"
        reason = "检测到 WebView：可先切换 WebView 上下文再用 UI 树，失败则 OCR"
    elif app_type == "hybrid":
        ui_tree_suitable = True
        strategy = "hybrid"
        reason = "混合应用：原生区域用 UI 树，Web 区域建议切换上下文或 OCR"
    elif app_type == "react_native":
        ui_tree_suitable = True
        strategy = "ui_tree"
        reason = "React Native：优先 UI 树（依赖 testID/无障碍属性），不足时用 OCR"
    elif app_type == "compose":
        ui_tree_suitable = stats["identifiable_ratio"] >= 0.1
        strategy = "ui_tree" if ui_tree_suitable else "hybrid"
        reason = "Jetpack Compose：优先 UI 树；若节点缺 contentDescription 则辅以 OCR"
    else:
        ui_tree_suitable = stats["identifiable_ratio"] >= 0.08 or stats["with_id"] >= 2
        strategy = "ui_tree" if ui_tree_suitable else "hybrid"
        reason = (
            "判定为原生 App，适合用 UI 树获取控件"
            if ui_tree_suitable
            else "疑似原生但无障碍属性偏少，建议 UI 树 + OCR 组合"
        )

    is_native = app_type in ("native", "compose")

    return {
        "package": pkg,
        "app_type": app_type,
        "app_type_label": {
            "native": "原生 App",
            "compose": "Compose 原生",
            "react_native": "React Native",
            "flutter": "Flutter",
            "webview": "WebView",
            "hybrid": "混合 App",
            "game": "游戏引擎",
            "canvas_custom": "自绘/地图类",
            "unknown": "未知",
        }.get(app_type, app_type),
        "is_native": is_native,
        "ui_tree_suitable": ui_tree_suitable,
        "recommended_strategy": strategy,
        "strategy_label": {
            "ui_tree": "UI 树拾取",
            "ocr": "OCR / 坐标拾取",
            "hybrid": "UI 树 + OCR 组合",
            "coordinate": "屏幕坐标",
        }.get(strategy, strategy),
        "strategy_reason": reason,
        "signals": signals,
        "stats": stats,
    }


def apply_strategy_to_inspect(profile: dict[str, Any], result: dict[str, Any]) -> dict[str, Any]:
    """把 App 画像写入 inspect 结果，并标记是否应改用 OCR。"""
    out = dict(result)
    out["app_profile"] = {
        "package": profile.get("package"),
        "app_type": profile.get("app_type"),
        "app_type_label": profile.get("app_type_label"),
        "is_native": profile.get("is_native"),
        "ui_tree_suitable": profile.get("ui_tree_suitable"),
        "recommended_strategy": profile.get("recommended_strategy"),
        "strategy_label": profile.get("strategy_label"),
        "strategy_reason": profile.get("strategy_reason"),
    }
    strategy = profile.get("recommended_strategy")
    weak = not result.get("valid") or result.get("inspect_error") in (
        "no_node_at_point", "generic_container", "ui_dump_failed", "cache_miss"
    )
    prefer_ocr = strategy in ("ocr", "coordinate") or (
        strategy == "hybrid" and weak and not profile.get("ui_tree_suitable")
    )
    if prefer_ocr and weak:
        out["prefer_ocr"] = True
        out["strategy_hint"] = profile.get("strategy_reason")
    # 固化展示用策略字段
    if not out.get("strategy_used"):
        if out.get("source") in ("ocr_screen", "ui_near_text") or out.get("locator_type") == "ocr":
            out["strategy_used"] = "ocr"
        elif out.get("locator_type") == "screen_ratio" or (
            (out.get("locators") or {}).get("screen_ratio") and weak
        ):
            out["strategy_used"] = "coordinate"
        else:
            out["strategy_used"] = "ui_tree"
    app_type = profile.get("app_type")
    if app_type in ("webview", "hybrid"):
        out.setdefault("context_hint", out.get("context_hint") or "WebView 区域：原生树多为外壳，建议 OCR 或手填")
    elif app_type in ("flutter", "game", "canvas_custom") and weak:
        out.setdefault("context_hint", out.get("context_hint") or profile.get("strategy_reason"))
    return out
