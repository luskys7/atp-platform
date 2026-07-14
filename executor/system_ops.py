"""Android 系统级原子操作：按键、剪贴板、亮屏/锁屏、屏幕状态断言。"""
import subprocess
import re

# Android KeyEvent 名称 -> keyevent 码（常用）
KEYCODES = {
    "back": "4",
    "home": "3",
    "recent": "187",
    "menu": "82",
    "power": "26",
    "volume_up": "24",
    "volume_down": "25",
    "enter": "66",
    "delete": "67",
    "wakeup": "224",
    "sleep": "223",
}


def _run_adb(serial: str, *args: str, capture: bool = False) -> subprocess.CompletedProcess:
    cmd = ["adb", "-s", serial, "shell"] + list(args)
    return subprocess.run(cmd, capture_output=capture, text=True, timeout=30, check=False)


def press_system_key(serial: str, key: str) -> None:
    code = KEYCODES.get(key.lower(), key)
    _run_adb(serial, "input", "keyevent", str(code))


def set_clipboard(serial: str, text: str) -> None:
    # Android 10+ cmd clipboard
    r = _run_adb(serial, "cmd", "clipboard", "set-text", text)
    if r.returncode != 0:
        safe = text.replace("'", "'\\''")
        _run_adb(serial, "am", "broadcast", "-a", "clipper.set", "-e", "text", safe)


def get_clipboard(serial: str) -> str:
    r = _run_adb(serial, "cmd", "clipboard", "get-text", capture=True)
    if r.returncode == 0 and r.stdout:
        return r.stdout.strip()
    return ""


def assert_clipboard(serial: str, expected: str, contains: bool = True) -> None:
    actual = get_clipboard(serial)
    ok = expected in actual if contains else actual == expected
    if not ok:
        raise AssertionError(f"Clipboard mismatch: expected={expected!r}, actual={actual!r}")


def wake_screen(serial: str) -> None:
    press_system_key(serial, "wakeup")
    _run_adb(serial, "input", "keyevent", "82")  # MENU 辅助唤醒


def lock_screen(serial: str) -> None:
    press_system_key(serial, "power")


def is_screen_on(serial: str) -> bool:
    r = _run_adb(serial, "dumpsys", "power", capture=True)
    if r.stdout:
        m = re.search(r"mHoldingDisplaySuspendBlocker=(true|false)", r.stdout)
        if m:
            return m.group(1) == "true"
        if "Display Power: state=ON" in r.stdout or "mScreenOn=true" in r.stdout:
            return True
    return False


def assert_screen_state(serial: str, expected: str) -> None:
    on = is_screen_on(serial)
    want_on = expected.lower() in ("on", "awake", "亮屏", "1", "true")
    if on != want_on:
        raise AssertionError(f"Screen state mismatch: expected={'on' if want_on else 'off'}, actual={'on' if on else 'off'}")


def assert_key_responded(serial: str, key: str, check_screen_change: bool = False) -> None:
    before = is_screen_on(serial) if check_screen_change else None
    press_system_key(serial, key)
    if check_screen_change and before is not None:
        import time
        time.sleep(0.5)
        after = is_screen_on(serial)
        if before == after and key in ("power", "wakeup", "sleep"):
            raise AssertionError(f"Key {key} did not change screen state")


STREAM_TYPE = {"music": 3, "media": 3, "ring": 2, "alarm": 4, "notification": 5}


def get_stream_volume(serial: str, stream: str = "music") -> int:
    """读取指定音频流音量（0~15 或设备最大值）。"""
    stream_type = STREAM_TYPE.get((stream or "music").lower(), 3)
    r = _run_adb(serial, "media", "volume", "--stream", str(stream_type), "--get", capture=True)
    if r.returncode == 0 and r.stdout:
        nums = re.findall(r"\d+", r.stdout.strip())
        if nums:
            return int(nums[-1])
    r2 = _run_adb(serial, "dumpsys", "audio", capture=True)
    if r2.stdout:
        label = f"STREAM_{stream.upper()}" if stream.lower() != "media" else "STREAM_MUSIC"
        for line in r2.stdout.splitlines():
            if label in line.upper():
                nums = re.findall(r":\s*(\d+)", line)
                if nums:
                    return int(nums[0])
                nums = re.findall(r"\b(\d{1,2})\b", line)
                if nums:
                    return int(nums[-1])
    r3 = _run_adb(serial, "settings", "get", "system", "volume_music", capture=True)
    if r3.returncode == 0 and r3.stdout.strip().isdigit():
        return int(r3.stdout.strip())
    return -1


def assert_volume(serial: str, expected: int, stream: str = "music", tolerance: int = 1) -> None:
    actual = get_stream_volume(serial, stream)
    if actual < 0:
        raise AssertionError(f"无法读取音量 stream={stream}")
    if abs(actual - int(expected)) > max(0, tolerance):
        raise AssertionError(
            f"Volume mismatch stream={stream}: expected={expected}±{tolerance}, actual={actual}"
        )


def assert_volume_change(
    serial: str,
    direction: str = "up",
    stream: str = "music",
    key: str | None = None,
    min_delta: int = 1,
) -> None:
    """按下音量键并校验音量实际变化，防止指令下发但未生效的假性通过。"""
    import time

    before = get_stream_volume(serial, stream)
    if before < 0:
        raise AssertionError(f"无法读取变更前音量 stream={stream}")
    press_key = key or ("volume_up" if direction.lower() in ("up", "increase", "+") else "volume_down")
    press_system_key(serial, press_key)
    time.sleep(0.35)
    after = get_stream_volume(serial, stream)
    if after < 0:
        raise AssertionError(f"无法读取变更后音量 stream={stream}")
    delta = after - before
    if direction.lower() in ("up", "increase", "+"):
        if delta < min_delta:
            raise AssertionError(
                f"Volume did not increase: before={before}, after={after}, key={press_key}"
            )
    else:
        if delta > -min_delta:
            raise AssertionError(
                f"Volume did not decrease: before={before}, after={after}, key={press_key}"
            )
