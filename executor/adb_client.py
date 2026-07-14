"""ADB 命令串行化，避免 uiautomator dump 与 u2 / input tap 并发导致崩溃或点击丢失。"""



from __future__ import annotations



import subprocess

import threading

import time

from pathlib import Path

from typing import Sequence



_adb_lock = threading.RLock()

_dump_gate = threading.Lock()

_last_interaction_at: dict[str, float] = {}

_last_u2_at: dict[str, float] = {}

_last_dump_finished_at: dict[str, float] = {}

_dump_inflight: set[str] = set()



INTERACTION_GUARD_SEC = 1.8

U2_DUMP_GUARD_SEC = 3.0

MIN_DUMP_INTERVAL_SEC = 3.0

TAP_PRIORITY_SEC = 3.0



_tap_priority_until: dict[str, float] = {}





def request_tap_priority(serial: str, seconds: float = TAP_PRIORITY_SEC) -> None:

    """用户点击优先：窗口内暂停 UI dump / 投屏抓帧，避免与 input tap 争抢 adb。"""

    until = time.monotonic() + max(0.5, seconds)

    prev = _tap_priority_until.get(serial, 0)

    if until > prev:

        _tap_priority_until[serial] = until





def tap_priority_active(serial: str) -> bool:

    return time.monotonic() < _tap_priority_until.get(serial, 0)





def note_adb_interaction(serial: str) -> None:

    _last_interaction_at[serial] = time.time()





def note_u2_session(serial: str) -> None:

    """uiautomator2 占用设备 UiAutomation 期间禁止 shell uiautomator dump。"""

    _last_u2_at[serial] = time.time()





def u2_guard_blocks_dump(serial: str) -> bool:

    return time.time() - _last_u2_at.get(serial, 0) < U2_DUMP_GUARD_SEC





def interaction_guard_blocks_dump(serial: str) -> bool:

    return time.time() - _last_interaction_at.get(serial, 0) < INTERACTION_GUARD_SEC





def _is_tap_shell(shell_args: Sequence[str]) -> bool:

    return len(shell_args) >= 2 and shell_args[0] == "input" and shell_args[1] == "tap"





def _run_subprocess(args: Sequence[str], *, timeout: float) -> subprocess.CompletedProcess:

    return subprocess.run(

        list(args),

        capture_output=True,

        timeout=timeout,

        check=False,

    )





def adb_run(args: Sequence[str], *, timeout: float = 10) -> subprocess.CompletedProcess:

    with _adb_lock:

        return _run_subprocess(args, timeout=timeout)





def adb_shell(serial: str, *shell_args: str, timeout: float = 10) -> subprocess.CompletedProcess:

    note_adb_interaction(serial)

    is_tap = _is_tap_shell(shell_args)

    if is_tap:

        request_tap_priority(serial, TAP_PRIORITY_SEC)

    args = ["adb", "-s", serial, "shell", *shell_args]

    if not is_tap:

        return adb_run(args, timeout=timeout)



    deadline = time.monotonic() + max(0.8, timeout)

    last_proc: subprocess.CompletedProcess | None = None

    for attempt in range(12):

        remaining = deadline - time.monotonic()

        if remaining <= 0:

            break

        wait_slice = min(0.05, remaining)

        acquired = _adb_lock.acquire(timeout=wait_slice)

        if acquired:

            try:

                last_proc = _run_subprocess(args, timeout=max(0.4, remaining))

                if last_proc.returncode == 0:

                    return last_proc

            finally:

                _adb_lock.release()

        request_tap_priority(serial, TAP_PRIORITY_SEC)

        if last_proc is not None and last_proc.returncode == 0:

            return last_proc

        if attempt >= 8:

            time.sleep(0.02)

    if last_proc is not None:

        return last_proc

    with _adb_lock:

        return _run_subprocess(args, timeout=max(0.5, timeout))





def adb_pull(serial: str, remote: str, local: str, timeout: float = 10) -> subprocess.CompletedProcess:

    return adb_run(["adb", "-s", serial, "pull", remote, local], timeout=timeout)





def _dump_slot_available(serial: str) -> bool:

    if tap_priority_active(serial):

        return False

    if serial in _dump_inflight:

        return False

    if u2_guard_blocks_dump(serial):

        return False

    if time.time() - _last_dump_finished_at.get(serial, 0) < MIN_DUMP_INTERVAL_SEC:

        return False

    return True





def _dump_ui_locked(serial: str, local_path: str, remote: str, timeout: float) -> bool:

    """单次 dump：每步 adb 命令独立持锁，点击优先窗口内立即放弃。"""

    if tap_priority_active(serial):

        return False

    dump = adb_run(

        ["adb", "-s", serial, "shell", "uiautomator", "dump", remote],

        timeout=timeout,

    )

    if dump.returncode != 0 or tap_priority_active(serial):

        return False

    pull = adb_run(

        ["adb", "-s", serial, "pull", remote, local_path],

        timeout=timeout,

    )

    return pull.returncode == 0 and Path(local_path).exists()





def adb_dump_ui(serial: str, local_path: str, remote: str = "/sdcard/atp_ui_dump.xml", timeout: float = 8) -> bool:

    """阻塞式 dump + pull；与 u2 / 用户点击互斥，同一设备全局串行。"""

    deadline = time.time() + min(timeout + 4, 14)

    while time.time() < deadline:

        if tap_priority_active(serial):

            return False

        if not _dump_slot_available(serial):

            time.sleep(0.12)

            continue

        with _dump_gate:

            if not _dump_slot_available(serial):

                continue

            _dump_inflight.add(serial)

        try:

            ok = _dump_ui_locked(serial, local_path, remote, timeout)

            if ok:

                _last_dump_finished_at[serial] = time.time()

                return True

        finally:

            with _dump_gate:

                _dump_inflight.discard(serial)

        time.sleep(0.12)

    return False





def try_adb_dump_ui(

    serial: str,

    local_path: str,

    remote: str = "/sdcard/atp_ui_dump.xml",

    timeout: float = 8,

) -> bool:

    """后台刷新：tap/u2 活跃或 dump 进行中时跳过，避免 UiAutomation already registered。"""

    if tap_priority_active(serial):

        return False

    if time.time() - _last_interaction_at.get(serial, 0) < INTERACTION_GUARD_SEC:

        return False

    with _dump_gate:

        if not _dump_slot_available(serial):

            return False

        _dump_inflight.add(serial)

    try:

        if tap_priority_active(serial):

            return False

        ok = _dump_ui_locked(serial, local_path, remote, timeout)

        if ok:

            _last_dump_finished_at[serial] = time.time()

        return ok

    finally:

        with _dump_gate:

            _dump_inflight.discard(serial)


