"""向中央平台上报本机 USB 设备（自动白名单 + executor_url）。"""

from __future__ import annotations

import logging
import os
import threading
import time
from typing import Any, Optional

import requests

from adb_discovery import list_usb_devices

logger = logging.getLogger("atp-executor.platform_sync")

_stop = threading.Event()
_thread: Optional[threading.Thread] = None


def _platform_url() -> str:
    return (
        os.environ.get("ATP_BACKEND_URL")
        or os.environ.get("PLATFORM_URL")
        or "http://localhost:8080"
    ).rstrip("/")


def _public_url(port: int) -> str:
    pub = (
        os.environ.get("EXECUTOR_PUBLIC_URL")
        or os.environ.get("ATP_EXECUTOR_PUBLIC_URL")
        or ""
    ).strip().rstrip("/")
    if pub:
        return pub
    return f"http://127.0.0.1:{port}"


def sync_once(port: int) -> dict[str, Any]:
    scan = list_usb_devices()
    devices = scan.get("devices") if isinstance(scan, dict) else []
    if not isinstance(devices, list):
        devices = []
    payload = {
        "executor_url": _public_url(port),
        "devices": devices,
    }
    url = f"{_platform_url()}/api/v1/executor/sync-devices"
    resp = requests.post(url, json=payload, timeout=15)
    resp.raise_for_status()
    body = resp.json() if resp.content else {}
    data = body.get("data") if isinstance(body, dict) else body
    logger.info(
        "platform sync ok: synced=%s url=%s",
        (data or {}).get("synced") if isinstance(data, dict) else "?",
        payload["executor_url"],
    )
    return data if isinstance(data, dict) else {"raw": data}


def _loop(port: int, interval: float) -> None:
    while not _stop.is_set():
        try:
            sync_once(port)
        except Exception as e:
            logger.warning("platform sync failed: %s", e)
        _stop.wait(interval)


def start_background_sync(port: int, interval: float = 10.0) -> None:
    global _thread
    if _thread and _thread.is_alive():
        return
    _stop.clear()
    _thread = threading.Thread(
        target=_loop, args=(port, interval), name="platform-sync", daemon=True
    )
    _thread.start()
    logger.info(
        "platform sync started → %s public=%s",
        _platform_url(),
        _public_url(port),
    )


def stop_background_sync() -> None:
    _stop.set()
