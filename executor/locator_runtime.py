"""定位链运行时 — 按优先级降级解析并输出结构化日志（M1）"""

from __future__ import annotations

import json
import re
import subprocess
import time
from typing import Any

import xml.etree.ElementTree as ET

from record_helper import (
    LOCATOR_CHAIN_PRIORITY,
    _ordered_locator_keys,
    _parse_bounds,
    dump_ui,
    dump_ui_cached,
    evaluate_locator,
)


def _log(kind: str, payload: dict[str, Any]) -> None:
    print(f"ATP_LOCATOR_{kind}:" + json.dumps(payload, ensure_ascii=False))


def _device_size(serial: str) -> tuple[int, int]:
    try:
        out = subprocess.check_output(
            ["adb", "-s", serial, "shell", "wm", "size"],
            text=True,
            timeout=8,
        )
        m = re.search(r"(\d+)x(\d+)", out)
        if m:
            return int(m.group(1)), int(m.group(2))
    except Exception:
        pass
    return 1080, 1920


def _ratio_to_xy(ratio: str, w: int, h: int) -> tuple[int, int] | None:
    parts = [p.strip() for p in str(ratio or "").split(",")]
    if len(parts) < 2:
        return None
    try:
        return int(float(parts[0]) * w), int(float(parts[1]) * h)
    except ValueError:
        return None


def _wait_condition_met(ev: dict, condition: str) -> bool:
    cond = (condition or "clickable").lower()
    if cond == "exists":
        return bool(ev.get("found"))
    if cond == "visible":
        return bool(ev.get("found") and ev.get("visible"))
    return bool(ev.get("clickable"))


def _resolve_once(serial: str, locators: dict, chain: list | None, display_name: str, element_name: str) -> dict[str, Any]:
    locs = dict(locators or {})
    if not locs:
        return {"ok": False, "error": "empty_locators", "attempts": []}

    xml = dump_ui_cached(serial, blocking=True) or dump_ui(serial)
    if not xml:
        return {"ok": False, "error": "ui_dump_failed", "attempts": []}
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return {"ok": False, "error": "xml_parse_failed", "attempts": []}

    dw, dh = _device_size(serial)
    ordered = _ordered_locator_keys(locs, chain)
    attempts: list[dict] = []
    last_found: dict | None = None

    for key in ordered:
        val = locs.get(key)
        if val is None or str(val).strip() == "":
            continue
        ev = evaluate_locator(root, key, str(val))
        attempts.append(ev)
        _log("ATTEMPT", {
            "type": key,
            "found": ev.get("found"),
            "visible": ev.get("visible"),
            "clickable": ev.get("clickable"),
            "reason": ev.get("reason") or "",
            "element": display_name or element_name,
        })

        if key == "screen_ratio" and ev.get("found"):
            xy = _ratio_to_xy(str(val), dw, dh)
            if xy:
                _log("MATCH", {"type": key, "x": xy[0], "y": xy[1], "element": display_name or element_name})
                return {"ok": True, "matched_by": key, "x": xy[0], "y": xy[1], "attempts": attempts}

        if ev.get("clickable") and ev.get("bounds"):
            _log("MATCH", {"type": key, "bounds": ev["bounds"], "element": display_name or element_name})
            return {"ok": True, "matched_by": key, "bounds": ev["bounds"], "attempts": attempts}

        if ev.get("found"):
            last_found = ev

    if last_found and last_found.get("bounds"):
        _log("MATCH", {
            "type": last_found.get("type"),
            "bounds": last_found["bounds"],
            "fallback": "found_not_clickable",
            "element": display_name or element_name,
        })
        return {
            "ok": True,
            "matched_by": last_found.get("type"),
            "bounds": last_found["bounds"],
            "attempts": attempts,
            "warning": "not_clickable",
        }

    _log("FAIL", {
        "error": "not_found",
        "element": display_name or element_name,
        "tried": [a.get("type") for a in attempts],
    })
    return {"ok": False, "error": "not_found", "attempts": attempts}


def resolve_locator_chain(
    serial: str,
    locators: dict | None,
    chain: list | None = None,
    *,
    display_name: str = "",
    element_name: str = "",
    wait_rule: dict | None = None,
) -> dict[str, Any]:
    """
    按定位链依次尝试，返回可点击目标。
    wait_rule: {condition: exists|visible|clickable, timeout_ms, interval_ms}
    """
    rule = dict(wait_rule or {})
    timeout_ms = int(rule.get("timeout_ms") or 0)
    if timeout_ms <= 0:
        return _resolve_once(serial, locators or {}, chain, display_name, element_name)

    condition = str(rule.get("condition") or "clickable")
    interval_ms = max(200, int(rule.get("interval_ms") or 500))
    deadline = time.time() + timeout_ms / 1000.0
    last: dict[str, Any] = {"ok": False, "error": "timeout", "attempts": []}
    while time.time() < deadline:
        last = _resolve_once(serial, locators or {}, chain, display_name, element_name)
        if last.get("ok"):
            if condition == "clickable" and last.get("warning") == "not_clickable":
                time.sleep(interval_ms / 1000.0)
                continue
            return last
        # 存在/可见：检查 attempts 是否满足
        attempts = last.get("attempts") or []
        if attempts and _wait_condition_met(attempts[-1], condition):
            return last
        time.sleep(interval_ms / 1000.0)
    last["error"] = "wait_timeout"
    _log("FAIL", {"error": "wait_timeout", "element": display_name or element_name, "condition": condition})
    return last


def chain_keys_for_locators(locators: dict, chain: list | None) -> list[str]:
    return _ordered_locator_keys(locators, chain) or [k for k in LOCATOR_CHAIN_PRIORITY if k in locators]
