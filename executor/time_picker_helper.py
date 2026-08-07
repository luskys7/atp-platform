"""相对时间选择器：将底部时间滚轮设为「当前时间 + N 分钟」。"""

from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from typing import Any


def compute_target_time(offset_minutes: int = 5, base: datetime | None = None) -> tuple[int, int, str]:
    """返回 (hour, minute, 'HH:mm')。"""
    now = base or datetime.now()
    target = now + timedelta(minutes=int(offset_minutes))
    hh, mm = target.hour, target.minute
    return hh, mm, f"{hh:02d}:{mm:02d}"


def _parse_bounds(bounds: str) -> tuple[int, int, int, int] | None:
    nums = [int(n) for n in re.findall(r"\d+", bounds or "")]
    if len(nums) >= 4:
        return nums[0], nums[1], nums[2], nums[3]
    return None


def _adb_shell(serial: str, *args: str, timeout: float = 8) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["adb", "-s", serial, "shell", *args],
        capture_output=True,
        timeout=timeout,
    )


def _dump_xml(serial: str) -> str:
    from ai_locator import dump_ui
    return dump_ui(serial) or ""


def _node_label(node: ET.Element) -> str:
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    return text or desc


def _parse_int_label(label: str) -> int | None:
    s = (label or "").strip()
    if not s:
        return None
    m = re.search(r"(\d{1,2})", s)
    if not m:
        return None
    return int(m.group(1))


def _is_number_picker(node: ET.Element) -> bool:
    clazz = (node.get("class") or "").lower()
    if "numberpicker" in clazz:
        return True
    if "wheel" in clazz:
        return True
    return "picker" in clazz and "time" in clazz


def _find_picker_columns(root: ET.Element) -> list[dict[str, Any]]:
    """找出可能的小时/分钟滚轮列（优先 NumberPicker）。"""
    screen_w = screen_h = 1
    for n in root.iter("node"):
        b = _parse_bounds(n.get("bounds") or "")
        if b:
            screen_w = max(screen_w, b[2])
            screen_h = max(screen_h, b[3])

    pickers: list[dict[str, Any]] = []
    for node in root.iter("node"):
        if not _is_number_picker(node):
            continue
        bounds = _parse_bounds(node.get("bounds") or "")
        if not bounds:
            continue
        x1, y1, x2, y2 = bounds
        if y1 < screen_h * 0.35:
            continue
        w, h = max(1, x2 - x1), max(1, y2 - y1)
        if h < 80 or w < 40:
            continue
        values: list[tuple[int, int, str]] = []
        for child in node.iter("node"):
            lab = _node_label(child)
            val = _parse_int_label(lab)
            cb = _parse_bounds(child.get("bounds") or "")
            if val is None or not cb:
                continue
            values.append(((cb[1] + cb[3]) // 2, val, lab))
        current = None
        if values:
            mid_y = (y1 + y2) // 2
            values.sort(key=lambda t: abs(t[0] - mid_y))
            current = values[0][1]
        pickers.append({
            "node": node,
            "bounds": bounds,
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "current": current,
            "y1": y1,
            "y2": y2,
        })

    if len(pickers) >= 2:
        pickers.sort(key=lambda p: p["bounds"][0])
        return pickers[:2]

    digit_nodes: list[dict[str, Any]] = []
    for node in root.iter("node"):
        lab = _node_label(node)
        val = _parse_int_label(lab)
        bounds = _parse_bounds(node.get("bounds") or "")
        if val is None or not bounds:
            continue
        x1, y1, x2, y2 = bounds
        if y1 < screen_h * 0.45:
            continue
        if not (0 <= val <= 59):
            continue
        digit_nodes.append({
            "val": val,
            "bounds": bounds,
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "label": lab,
        })

    if not digit_nodes:
        return []

    xs = sorted(d["cx"] for d in digit_nodes)
    mid_x = (xs[0] + xs[-1]) / 2 if len(xs) >= 2 else xs[0]
    left = [d for d in digit_nodes if d["cx"] <= mid_x]
    right = [d for d in digit_nodes if d["cx"] > mid_x]
    cols: list[dict[str, Any]] = []
    for group in (left, right):
        if not group:
            continue
        ys = [g["cy"] for g in group]
        mid_y = (min(ys) + max(ys)) / 2
        group.sort(key=lambda g: abs(g["cy"] - mid_y))
        current = group[0]["val"]
        x1 = min(g["bounds"][0] for g in group)
        y1 = min(g["bounds"][1] for g in group) - 40
        x2 = max(g["bounds"][2] for g in group)
        y2 = max(g["bounds"][3] for g in group) + 40
        cols.append({
            "node": None,
            "bounds": (x1, y1, x2, y2),
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "current": current,
            "y1": y1,
            "y2": y2,
        })
    cols.sort(key=lambda c: c["bounds"][0])
    return cols[:2]


def _read_column_current(serial: str, col_index: int) -> int | None:
    xml = _dump_xml(serial)
    if not xml:
        return None
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    cols = _find_picker_columns(root)
    if col_index >= len(cols):
        return None
    return cols[col_index].get("current")


def _swipe_column(serial: str, col: dict[str, Any], direction: str, distance: int = 120) -> None:
    """direction=up 增大显示值；down 减小。"""
    cx = int(col["cx"])
    cy = int(col["cy"])
    half = max(40, min(distance, max(60, (col["y2"] - col["y1"]) // 3)))
    if direction == "up":
        y1, y2 = cy + half // 2, cy - half // 2
    else:
        y1, y2 = cy - half // 2, cy + half // 2
    _adb_shell(serial, "input", "swipe", str(cx), str(y1), str(cx), str(y2), "180", timeout=6)
    time.sleep(0.22)


def _scroll_column_to(
    serial: str,
    col_index: int,
    target: int,
    *,
    modulo: int,
    max_steps: int = 40,
) -> bool:
    last = None
    stagnant = 0
    for _ in range(max_steps):
        xml = _dump_xml(serial)
        if not xml:
            return False
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return False
        cols = _find_picker_columns(root)
        if col_index >= len(cols):
            return False
        col = cols[col_index]
        current = col.get("current")
        if current is None:
            _swipe_column(serial, col, "up", 80)
            continue
        if current == target:
            return True

        forward = (target - current) % modulo
        backward = (current - target) % modulo
        if forward <= backward:
            direction = "up"
            steps_hint = min(forward, 6)
        else:
            direction = "down"
            steps_hint = min(backward, 6)

        dist = 90 + min(steps_hint, 4) * 25
        _swipe_column(serial, col, direction, dist)

        if current == last:
            stagnant += 1
            if stagnant >= 4:
                _swipe_column(serial, col, "down" if direction == "up" else "up", 140)
                stagnant = 0
        else:
            stagnant = 0
        last = current
    final = _read_column_current(serial, col_index)
    return final == target


def _tap_confirm(serial: str) -> bool:
    xml = _dump_xml(serial)
    if not xml:
        return False
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return False
    for node in root.iter("node"):
        lab = _node_label(node)
        if lab in ("确定", "完成", "OK", "Done", "确认"):
            bounds = _parse_bounds(node.get("bounds") or "")
            if not bounds:
                continue
            cx = (bounds[0] + bounds[2]) // 2
            cy = (bounds[1] + bounds[3]) // 2
            _adb_shell(serial, "input", "tap", str(cx), str(cy), timeout=6)
            return True
    return False


def set_relative_time(
    serial: str,
    offset_minutes: int = 5,
    confirm: bool = False,
) -> dict[str, Any]:
    """
    将当前前台时间选择器滚到「现在 + offset_minutes」。
    打印 ATP_VAR_OUT:TIME_HH / TIME_MM / TIME_HM 供脚本变量使用。
    """
    if not serial:
        raise RuntimeError("设备序列号为空，无法设置相对时间")

    hh, mm, hm = compute_target_time(offset_minutes)
    print(f"ATP_RELATIVE_TIME_TARGET:{hm} (offset={offset_minutes}m)")

    xml = _dump_xml(serial)
    if not xml:
        raise RuntimeError(f"无法 dump UI，不能将时间选择器调到 {hm}")
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as e:
        raise RuntimeError(f"UI dump 解析失败，不能将时间选择器调到 {hm}") from e

    cols = _find_picker_columns(root)
    if len(cols) < 2:
        raise RuntimeError(f"未识别到时/分滚轮，无法将时间选择器调到 {hm}")

    ok_h = _scroll_column_to(serial, 0, hh, modulo=24)
    ok_m = _scroll_column_to(serial, 1, mm, modulo=60)
    if not ok_h or not ok_m:
        cur_h = _read_column_current(serial, 0)
        cur_m = _read_column_current(serial, 1)
        raise RuntimeError(
            f"无法将时间选择器调到 {hm}（当前≈{cur_h}:{cur_m}）"
        )

    confirmed = False
    if confirm:
        confirmed = _tap_confirm(serial)
        if not confirmed:
            print("ATP_RELATIVE_TIME_WARN:confirm_button_not_found")

    return {
        "ok": True,
        "hour": hh,
        "minute": mm,
        "time": hm,
        "offset_minutes": int(offset_minutes),
        "confirmed": confirmed,
    }
