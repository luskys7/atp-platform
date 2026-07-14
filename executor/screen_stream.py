"""WebSocket 设备投屏 - ADB screencap 帧流（JPEG 压缩 + 自适应帧率）"""

import asyncio
import io
import json
import logging
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

from adb_client import adb_shell, request_tap_priority, tap_priority_active
from execution_context import calibrate_point, parse_calibration_json

logger = logging.getLogger("atp-screen")

VERIFY_URL = None  # 由 main.py 注入

# 可通过环境变量调节：ATP_SCREEN_FPS / ATP_SCREEN_JPEG_QUALITY / ATP_SCREEN_MAX_WIDTH
DEFAULT_FPS = int(os.environ.get("ATP_SCREEN_FPS", "15"))
JPEG_QUALITY = int(os.environ.get("ATP_SCREEN_JPEG_QUALITY", "85"))
MAX_WIDTH = int(os.environ.get("ATP_SCREEN_MAX_WIDTH", "1080"))

try:
    from PIL import Image

    HAS_PIL = True
except ImportError:
    HAS_PIL = False


def set_verify_url(url: str):
    global VERIFY_URL
    VERIFY_URL = url


def verify_token(token: str, serial: str) -> bool:
    if not VERIFY_URL:
        return True
    try:
        qs = urllib.parse.urlencode({"token": token, "serial": serial})
        with urllib.request.urlopen(f"{VERIFY_URL}?{qs}", timeout=5) as resp:
            body = json.loads(resp.read().decode())
            data = body.get("data") or body
            return bool(data.get("valid"))
    except (urllib.error.URLError, json.JSONDecodeError, KeyError) as e:
        logger.warning("token verify failed: %s", e)
        return False


async def capture_frame_raw(serial: str) -> bytes:
    if tap_priority_active(serial):
        return b""
    proc = await asyncio.create_subprocess_exec(
        "adb", "-s", serial, "exec-out", "screencap", "-p",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL,
    )
    stdout, _ = await proc.communicate()
    return stdout or b""


def _encode_jpeg(png_bytes: bytes, quality: int = JPEG_QUALITY, max_width: int = MAX_WIDTH) -> bytes:
    if not HAS_PIL or not png_bytes:
        return png_bytes
    try:
        img = Image.open(io.BytesIO(png_bytes))
        if img.width > max_width:
            ratio = max_width / img.width
            img = img.resize((max_width, max(1, int(img.height * ratio))), Image.Resampling.BILINEAR)
        buf = io.BytesIO()
        img.convert("RGB").save(buf, format="JPEG", quality=quality, optimize=True)
        return buf.getvalue()
    except Exception as e:
        logger.debug("jpeg encode fallback: %s", e)
        return png_bytes


async def capture_frame(serial: str) -> bytes:
    raw = await capture_frame_raw(serial)
    if not raw:
        return b""
    if HAS_PIL:
        return _encode_jpeg(raw)
    return raw


async def stream_screen(websocket, serial: str, fps: int = DEFAULT_FPS):
    """自适应帧率：capture 耗时超过目标间隔时不额外 sleep，尽量降低延迟。"""
    await websocket.send_text('{"mode":"jpeg"}')
    target = 1.0 / max(fps, 1)
    sending = False
    while True:
        t0 = time.perf_counter()
        frame = await capture_frame(serial)
        if frame and not sending:
            sending = True
            try:
                await websocket.send_bytes(frame)
            finally:
                sending = False
        elapsed = time.perf_counter() - t0
        sleep = target - elapsed
        if sleep > 0.001:
            await asyncio.sleep(sleep)


def adb_tap(serial: str, x: int, y: int, calibration_json: str | None = None):
    request_tap_priority(serial)
    cal = parse_calibration_json(calibration_json)
    x, y = calibrate_point(x, y, cal)
    last_err = ""
    for attempt in range(3):
        proc = adb_shell(serial, "input", "tap", str(x), str(y), timeout=5)
        if proc.returncode == 0:
            return
        last_err = (proc.stderr or proc.stdout or b"").decode("utf-8", errors="ignore").strip()
        if attempt < 2:
            time.sleep(0.03 * (attempt + 1))
    raise RuntimeError(f"adb tap failed @ ({x},{y}): {last_err or 'unknown'}")


def adb_swipe(serial: str, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300):
    request_tap_priority(serial)
    adb_shell(
        serial, "input", "swipe",
        str(x1), str(y1), str(x2), str(y2), str(duration_ms),
        timeout=10,
    )


def adb_input_text(serial: str, text: str, focus_x: int | None = None, focus_y: int | None = None):
    from text_input_helper import adb_input_text as _input
    return _input(serial, text, focus_x, focus_y)
