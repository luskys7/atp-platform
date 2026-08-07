"""WebSocket 设备投屏 - ADB screencap 帧流（JPEG 压缩 + 自适应帧率）"""

import asyncio
import io
import json
import logging
import os
import struct
import time
import urllib.error
import urllib.parse
import urllib.request

from adb_client import adb_shell, request_tap_priority, tap_priority_active
from execution_context import calibrate_point, parse_calibration_json

logger = logging.getLogger("atp-screen")

VERIFY_URL = None  # 由 main.py 注入

# 可通过环境变量调节：ATP_SCREEN_FPS / ATP_SCREEN_JPEG_QUALITY / ATP_SCREEN_MAX_WIDTH
# 默认偏保守：录制/连点时与 screencap 抢 ADB，过高帧率只会堆积延迟让页面更卡
DEFAULT_FPS = int(os.environ.get("ATP_SCREEN_FPS", "8"))
JPEG_QUALITY = int(os.environ.get("ATP_SCREEN_JPEG_QUALITY", "55"))
MAX_WIDTH = int(os.environ.get("ATP_SCREEN_MAX_WIDTH", "720"))

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


def _parse_raw_screencap(data: bytes):
    """解析 adb exec-out screencap（非 -p）原始帧，失败返回 None。"""
    if not data or len(data) < 12:
        return None
    try:
        width, height, fmt = struct.unpack_from("<III", data, 0)
        if width <= 0 or height <= 0 or width > 10000 or height > 10000:
            return None
        # 1=RGBA_8888, 2=RGBX_8888, 3=RGB_888（少见）
        bpp = 4 if fmt in (1, 2, 0) else (3 if fmt == 3 else 0)
        if bpp == 0:
            # 部分机型头部后直接 RGBA
            bpp = 4
            body = data[12:]
            need = width * height * 4
            if len(body) < need:
                return None
            img = Image.frombytes("RGBA", (width, height), body[:need], "raw", "RGBA")
            return img.convert("RGB")
        need = width * height * bpp
        body = data[12:]
        if len(body) < need:
            return None
        body = body[:need]
        if bpp == 4:
            img = Image.frombytes("RGBA", (width, height), body, "raw", "RGBA")
            return img.convert("RGB")
        img = Image.frombytes("RGB", (width, height), body, "raw", "RGB")
        return img
    except Exception as e:
        logger.debug("raw screencap parse failed: %s", e)
        return None


async def capture_frame_raw_png(serial: str) -> bytes:
    proc = await asyncio.create_subprocess_exec(
        "adb", "-s", serial, "exec-out", "screencap", "-p",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL,
    )
    stdout, _ = await proc.communicate()
    return stdout or b""


async def capture_frame_raw_bytes(serial: str) -> bytes:
    proc = await asyncio.create_subprocess_exec(
        "adb", "-s", serial, "exec-out", "screencap",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL,
    )
    stdout, _ = await proc.communicate()
    return stdout or b""


def _encode_jpeg_image(img: "Image.Image", quality: int = JPEG_QUALITY, max_width: int = MAX_WIDTH) -> bytes:
    if img.width > max_width:
        ratio = max_width / img.width
        img = img.resize((max_width, max(1, int(img.height * ratio))), Image.Resampling.BILINEAR)
    if img.mode != "RGB":
        img = img.convert("RGB")
    buf = io.BytesIO()
    # optimize=True 极慢（可达数十 ms），投屏场景关闭
    img.save(buf, format="JPEG", quality=quality, optimize=False)
    return buf.getvalue()


def _encode_jpeg(png_bytes: bytes, quality: int = JPEG_QUALITY, max_width: int = MAX_WIDTH) -> bytes:
    if not HAS_PIL or not png_bytes:
        return png_bytes
    try:
        img = Image.open(io.BytesIO(png_bytes))
        return _encode_jpeg_image(img, quality=quality, max_width=max_width)
    except Exception as e:
        logger.debug("jpeg encode fallback: %s", e)
        return png_bytes


async def capture_frame(serial: str) -> bytes:
    """PNG screencap + 轻量 JPEG。raw RGBA 体积过大，在高分屏上往往更慢。"""
    png = await capture_frame_raw_png(serial)
    if not png:
        return b""
    if HAS_PIL:
        return await asyncio.to_thread(_encode_jpeg, png)
    return png


# 兼容旧调用名
capture_frame_raw = capture_frame_raw_png


async def stream_screen(websocket, serial: str, fps: int = DEFAULT_FPS):
    """自适应帧率：capture 耗时超过目标间隔时不额外 sleep；交互高峰主动降帧。"""
    await websocket.send_text(json.dumps({
        "mode": "jpeg",
        "max_width": MAX_WIDTH,
        "jpeg_quality": JPEG_QUALITY,
        "target_fps": fps,
    }))
    base_target = 1.0 / max(fps, 1)
    sending = False
    while True:
        # 点击/输入优先时跳过本帧，把 ADB 让给交互，减轻录制卡顿
        if tap_priority_active(serial):
            await asyncio.sleep(0.05)
            continue
        t0 = time.perf_counter()
        frame = await capture_frame(serial)
        if frame and not sending:
            sending = True
            try:
                await websocket.send_bytes(frame)
            finally:
                sending = False
        elapsed = time.perf_counter() - t0
        # 捕获越慢，目标间隔越大，避免 ADB 队列打满
        target = max(base_target, min(0.35, elapsed * 1.05))
        sleep = target - elapsed
        if sleep > 0.001:
            await asyncio.sleep(sleep)


def adb_tap(serial: str, x: int, y: int, calibration_json: str | None = None):
    # 短优先窗口：挡住 dump，但不长时间冻住交互
    request_tap_priority(serial, 0.6)
    cal = parse_calibration_json(calibration_json)
    x, y = calibrate_point(x, y, cal)
    last_err = ""
    for attempt in range(3):
        proc = adb_shell(serial, "input", "tap", str(x), str(y), timeout=5)
        if proc.returncode == 0:
            return
        last_err = (proc.stderr or proc.stdout or b"").decode("utf-8", errors="ignore").strip()
        if attempt < 2:
            time.sleep(0.05 * (attempt + 1))
    raise RuntimeError(last_err or "adb tap failed")


def adb_swipe(serial: str, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300,
              calibration_json: str | None = None):
    request_tap_priority(serial, 0.8)
    cal = parse_calibration_json(calibration_json)
    x1, y1 = calibrate_point(x1, y1, cal)
    x2, y2 = calibrate_point(x2, y2, cal)
    proc = adb_shell(
        serial, "input", "swipe",
        str(x1), str(y1), str(x2), str(y2), str(int(duration_ms)),
        timeout=8,
    )
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or b"").decode("utf-8", errors="ignore").strip()
        raise RuntimeError(err or "adb swipe failed")
