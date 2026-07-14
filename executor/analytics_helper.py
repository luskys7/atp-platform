"""埋点校验：logcat 抓取 + 可选 HTTP 校验端点"""

from __future__ import annotations

import json
import subprocess
import urllib.error
import urllib.request
from typing import Any, Dict, List


def _run(serial: str, *args, timeout=8) -> subprocess.CompletedProcess:
    if not serial:
        return subprocess.CompletedProcess(args, 0, "", "")
    try:
        return subprocess.run(
            ["adb", "-s", serial, "shell"] + list(args),
            capture_output=True, text=True, timeout=timeout,
        )
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return subprocess.CompletedProcess(args, 1, "", "adb error")


def _logcat_dump(serial: str) -> str:
    if not serial:
        return ""
    try:
        proc = subprocess.run(
            ["adb", "-s", serial, "logcat", "-d", "-t", "200"],
            capture_output=True, text=True, timeout=10,
        )
        return proc.stdout or ""
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return ""


def assert_analytics_event(
    serial: str,
    event_name: str,
    expected_props: Dict[str, Any] | None = None,
    verify_url: str = "",
    timeout: int = 15,
) -> List[str]:
    logs: List[str] = []
    expected_props = expected_props or {}

    if verify_url:
        try:
            req = urllib.request.Request(verify_url, method="GET")
            with urllib.request.urlopen(req, timeout=min(timeout, 15)) as resp:
                body = resp.read().decode("utf-8", errors="ignore")
            payload = json.loads(body) if body.strip().startswith("{") else {"raw": body}
            events = payload.get("events") or payload.get("data") or []
            if isinstance(events, dict):
                events = [events]
            matched = False
            for ev in events:
                if not isinstance(ev, dict):
                    continue
                name = ev.get("event") or ev.get("event_name") or ev.get("name")
                if name != event_name:
                    continue
                props = ev.get("props") or ev.get("properties") or {}
                if all(str(props.get(k)) == str(v) for k, v in expected_props.items()):
                    matched = True
                    break
            if not matched:
                raise AssertionError(f"埋点 HTTP 校验未命中: {event_name}")
            logs.append(f"analytics http OK: {event_name}")
            return logs
        except urllib.error.URLError as e:
            raise AssertionError(f"埋点校验 URL 不可达: {verify_url} ({e})") from e

    text = _logcat_dump(serial)
    if event_name not in text:
        raise AssertionError(f"logcat 未找到埋点事件: {event_name}")
    logs.append(f"analytics logcat OK: {event_name}")
    for key, val in expected_props.items():
        marker = f'"{key}":"{val}"'
        alt = f"{key}={val}"
        if marker not in text and alt not in text and str(val) not in text:
            raise AssertionError(f"埋点属性未匹配: {key}={val}")
    if expected_props:
        logs.append(f"analytics props OK: {json.dumps(expected_props, ensure_ascii=False)}")
    return logs
