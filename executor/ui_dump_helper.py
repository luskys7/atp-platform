"""统一 UI 树 dump：优先 uiautomator2.dump_hierarchy，失败回退 shell uiautomator dump。"""

from __future__ import annotations

import logging
import tempfile
import threading
import time
from pathlib import Path
from typing import Any

from adb_client import (
    note_u2_session,
    tap_priority_active,
    _dump_gate,
    _dump_inflight,
    _last_dump_finished_at,
    _dump_ui_locked,
    MIN_DUMP_INTERVAL_SEC,
)

logger = logging.getLogger("atp.ui_dump")

_last_dump_meta: dict[str, dict[str, Any]] = {}
_u2_devices: dict[str, Any] = {}
_u2_lock = threading.Lock()


def get_last_dump_meta(serial: str) -> dict[str, Any]:
    return dict(_last_dump_meta.get(serial) or {})


def _set_meta(serial: str, **kwargs: Any) -> None:
    prev = _last_dump_meta.get(serial) or {}
    prev.update(kwargs)
    prev["at"] = time.time()
    _last_dump_meta[serial] = prev


def _slot_available(serial: str, *, for_u2: bool) -> bool:
    """u2 hierarchy 不受 shell 的 U2_DUMP_GUARD 阻塞；仅与点击 / 进行中 dump 互斥。"""
    if tap_priority_active(serial):
        return False
    if serial in _dump_inflight:
        return False
    if not for_u2:
        from adb_client import u2_guard_blocks_dump
        if u2_guard_blocks_dump(serial):
            return False
    if time.time() - _last_dump_finished_at.get(serial, 0) < MIN_DUMP_INTERVAL_SEC:
        # 阻塞拾取允许强制跳过间隔（由 force 参数控制）
        return False
    return True


def _get_u2_device(serial: str):
    with _u2_lock:
        d = _u2_devices.get(serial)
        if d is not None:
            return d
    import uiautomator2 as u2
    note_u2_session(serial)
    d = u2.connect(serial)
    with _u2_lock:
        _u2_devices[serial] = d
    return d


def clear_u2_device(serial: str) -> None:
    with _u2_lock:
        _u2_devices.pop(serial, None)


def _dump_via_u2(serial: str, local_path: str, timeout: float) -> tuple[bool, str]:
    """返回 (ok, error_message)。"""
    try:
        import uiautomator2 as u2  # noqa: F401
    except ImportError:
        return False, "uiautomator2 未安装，请在执行器执行 pip install uiautomator2"

    if tap_priority_active(serial):
        return False, "tap_priority"

    note_u2_session(serial)
    try:
        d = _get_u2_device(serial)
        # dump_hierarchy 可能触发设备端 ATX 首次安装
        xml = d.dump_hierarchy(compressed=False)
        if not xml or len(xml) < 40:
            return False, "u2 hierarchy 为空"
        Path(local_path).write_text(xml, encoding="utf-8")
        note_u2_session(serial)
        return True, ""
    except Exception as e:
        clear_u2_device(serial)
        msg = str(e) or e.__class__.__name__
        logger.warning("u2 dump_hierarchy failed serial=%s: %s", serial, msg)
        # 常见：设备未授权、ATX 未起、版本问题
        lower = msg.lower()
        if "not found" in lower or "offline" in lower or "not online" in lower:
            return False, f"设备不可用: {msg}"
        if "uiautomator" in lower or "atx" in lower or "instrumentation" in lower:
            return False, f"手机端 uiautomator2/ATX 服务异常（首次可能需在手机点允许安装）: {msg}"
        return False, f"u2 dump 失败: {msg}"
    finally:
        note_u2_session(serial)


def dump_ui_xml(
    serial: str,
    local_path: str | None = None,
    *,
    remote: str = "/sdcard/atp_ui_dump.xml",
    timeout: float = 12,
    prefer_u2: bool = True,
    force: bool = False,
    allow_shell_fallback: bool = True,
) -> dict[str, Any]:
    """
    统一 dump 入口。
    返回: {ok, source: u2|shell|fail, error, local_path, bytes}
    """
    if not serial:
        return {"ok": False, "source": "fail", "error": "missing_serial", "local_path": "", "bytes": 0}

    path = Path(local_path) if local_path else Path(tempfile.gettempdir()) / f"atp_ui_{serial}.xml"
    path.parent.mkdir(parents=True, exist_ok=True)

    deadline = time.time() + (timeout + 6 if force else min(timeout + 4, 16))
    last_error = ""

    while time.time() < deadline:
        if tap_priority_active(serial) and not force:
            last_error = "tap_priority"
            time.sleep(0.1)
            continue

        acquired = False
        with _dump_gate:
            busy = serial in _dump_inflight
            if not busy:
                if not force:
                    # u2 路径：不因「刚做过 u2 输入」而永久跳过
                    if prefer_u2:
                        if tap_priority_active(serial):
                            busy = True
                        elif time.time() - _last_dump_finished_at.get(serial, 0) < MIN_DUMP_INTERVAL_SEC:
                            busy = True
                    else:
                        from adb_client import u2_guard_blocks_dump
                        if tap_priority_active(serial) or u2_guard_blocks_dump(serial):
                            busy = True
                        elif time.time() - _last_dump_finished_at.get(serial, 0) < MIN_DUMP_INTERVAL_SEC:
                            busy = True
                if not busy:
                    _dump_inflight.add(serial)
                    acquired = True

        if not acquired:
            time.sleep(0.12)
            continue

        try:
            if prefer_u2:
                ok, err = _dump_via_u2(serial, str(path), timeout)
                if ok:
                    _last_dump_finished_at[serial] = time.time()
                    size = path.stat().st_size if path.exists() else 0
                    _set_meta(serial, source="u2", error="", bytes=size)
                    return {
                        "ok": True,
                        "source": "u2",
                        "error": "",
                        "local_path": str(path),
                        "bytes": size,
                    }
                last_error = err or "u2_failed"
                # 设备明显不可用时直接结束，避免空转超时
                if any(k in (err or "").lower() for k in ("not online", "not found", "device不可用", "offline", "no devices")):
                    if allow_shell_fallback:
                        # 仍尝试一次 shell，然后结束
                        ok_shell = _dump_ui_locked(serial, str(path), remote, min(timeout, 6))
                        if ok_shell:
                            _last_dump_finished_at[serial] = time.time()
                            size = path.stat().st_size if path.exists() else 0
                            _set_meta(serial, source="shell", error="", bytes=size)
                            return {
                                "ok": True,
                                "source": "shell",
                                "error": "",
                                "local_path": str(path),
                                "bytes": size,
                            }
                    _set_meta(serial, source="fail", error=last_error, bytes=0)
                    return {
                        "ok": False,
                        "source": "fail",
                        "error": last_error,
                        "local_path": str(path),
                        "bytes": 0,
                    }
                if not allow_shell_fallback:
                    _set_meta(serial, source="fail", error=last_error, bytes=0)
                    return {
                        "ok": False,
                        "source": "fail",
                        "error": last_error,
                        "local_path": str(path),
                        "bytes": 0,
                    }

            # shell 回退（需避开 u2 占用窗口）
            from adb_client import u2_guard_blocks_dump
            if u2_guard_blocks_dump(serial) and not force:
                time.sleep(0.25)
                last_error = last_error or "u2_guard_shell"
                continue

            ok_shell = _dump_ui_locked(serial, str(path), remote, timeout)
            if ok_shell:
                _last_dump_finished_at[serial] = time.time()
                size = path.stat().st_size if path.exists() else 0
                _set_meta(serial, source="shell", error="", bytes=size)
                return {
                    "ok": True,
                    "source": "shell",
                    "error": "",
                    "local_path": str(path),
                    "bytes": size,
                }
            last_error = last_error or "shell_dump_failed"
        finally:
            with _dump_gate:
                _dump_inflight.discard(serial)

        time.sleep(0.12)

    _set_meta(serial, source="fail", error=last_error or "timeout", bytes=0)
    return {
        "ok": False,
        "source": "fail",
        "error": last_error or "timeout",
        "local_path": str(path),
        "bytes": 0,
    }


def dump_ui_text(serial: str, *, prefer_u2: bool = True, force: bool = False, timeout: float = 12) -> str:
    """兼容旧 dump_ui：返回 XML 文本，失败返回空串。"""
    res = dump_ui_xml(serial, prefer_u2=prefer_u2, force=force, timeout=timeout)
    if not res.get("ok"):
        return ""
    try:
        return Path(res["local_path"]).read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return ""


def try_dump_ui_xml(serial: str, local_path: str, **kwargs: Any) -> dict[str, Any]:
    """后台刷新：点击活跃时跳过。"""
    if tap_priority_active(serial):
        return {"ok": False, "source": "fail", "error": "tap_priority", "local_path": local_path, "bytes": 0}
    from adb_client import interaction_guard_blocks_dump
    if interaction_guard_blocks_dump(serial):
        return {"ok": False, "source": "fail", "error": "interaction_guard", "local_path": local_path, "bytes": 0}
    return dump_ui_xml(serial, local_path, force=False, **kwargs)


def prepare_ui_dump(serial: str) -> dict[str, Any]:
    """首次连接 u2 并验证 hierarchy，供「刷新 UI 树」预热。"""
    if not serial:
        return {"ok": False, "source": "fail", "error": "missing_serial", "message": "缺少设备序列号"}

    try:
        import uiautomator2 as u2  # noqa: F401
    except ImportError:
        # 仍尝试 shell
        res = dump_ui_xml(serial, prefer_u2=False, force=True, allow_shell_fallback=True)
        return {
            "ok": bool(res.get("ok")),
            "source": res.get("source"),
            "error": res.get("error") or "uiautomator2 未安装",
            "message": "uiautomator2 未安装，已回退 shell dump" if res.get("ok") else "uiautomator2 未安装且 shell dump 失败",
            "need_user_action": False,
            "dump_source": res.get("source"),
            "bytes": res.get("bytes", 0),
        }

    clear_u2_device(serial)
    res = dump_ui_xml(serial, prefer_u2=True, force=True, allow_shell_fallback=True, timeout=20)
    need_user = False
    err = res.get("error") or ""
    if not res.get("ok") or res.get("source") == "shell":
        lower = err.lower()
        if any(k in lower for k in ("atx", "uiautomator", "instrumentation", "install", "permission", "授权")):
            need_user = True
        if res.get("source") == "shell" and res.get("ok"):
            msg = "u2 未就绪，已回退系统 uiautomator dump；可检查手机是否弹出安装/授权提示后重试"
        elif not res.get("ok"):
            msg = err or "UI 树预热失败"
        else:
            msg = "uiautomator2 层级已就绪"
    else:
        msg = "uiautomator2 层级已就绪（完整控件树）"

    return {
        "ok": bool(res.get("ok")),
        "source": res.get("source"),
        "dump_source": res.get("source"),
        "error": err,
        "message": msg,
        "need_user_action": need_user,
        "bytes": res.get("bytes", 0),
        "hint": "首次连接可能向手机推送 ATX/uiautomator2 组件，请在手机上点允许",
    }
