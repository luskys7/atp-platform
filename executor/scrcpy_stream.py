"""scrcpy H.264 低延迟投屏 — 需 scrcpy-server（随 scrcpy 安装或放入 data/scrcpy/）"""

from __future__ import annotations

import asyncio
import base64
import json
import logging
import os
import random
import re
import shutil
import struct
from pathlib import Path

from starlette.websockets import WebSocketDisconnect

logger = logging.getLogger("atp-scrcpy")

EXECUTOR_DIR = Path(__file__).resolve().parent
REMOTE_JAR = "/data/local/tmp/scrcpy-server.jar"

SERVER_VERSION = os.environ.get("ATP_SCRCPY_SERVER_VERSION", "2.4")
MAX_SIZE = int(os.environ.get("ATP_SCRCPY_MAX_SIZE", "1080"))
MAX_FPS = int(os.environ.get("ATP_SCRCPY_MAX_FPS", "30"))
BIT_RATE = int(os.environ.get("ATP_SCRCPY_BIT_RATE", "12000000"))

_active: dict[str, asyncio.subprocess.Process] = {}


def find_scrcpy_server() -> Path | None:
    custom = os.environ.get("ATP_SCRCPY_SERVER")
    if custom and Path(custom).is_file():
        return Path(custom)
    bundled = EXECUTOR_DIR / "data" / "scrcpy" / "scrcpy-server"
    if bundled.is_file():
        return bundled
    bundled_jar = EXECUTOR_DIR / "data" / "scrcpy" / "scrcpy-server.jar"
    if bundled_jar.is_file():
        return bundled_jar
    scrcpy_bin = shutil.which("scrcpy")
    if scrcpy_bin:
        for name in ("scrcpy-server", "scrcpy-server.jar"):
            candidate = Path(scrcpy_bin).parent / name
            if candidate.is_file():
                return candidate
    return None


def is_available() -> bool:
    return find_scrcpy_server() is not None


def _nal_type(nal: bytes) -> int:
    return nal[0] & 0x1F if nal else -1


def _build_avcc(sps: bytes, pps: bytes) -> bytes:
    return bytes([
        1,
        sps[1] if len(sps) > 1 else 0x42,
        sps[2] if len(sps) > 2 else 0x00,
        sps[3] if len(sps) > 3 else 0x1E,
        0xFF,
        0xE1,
    ]) + struct.pack(">H", len(sps)) + sps + bytes([1]) + struct.pack(">H", len(pps)) + pps


def _to_avcc(nals: list[bytes]) -> bytes:
    out = bytearray()
    for nal in nals:
        out.extend(struct.pack(">I", len(nal)))
        out.extend(nal)
    return bytes(out)


class _AnnexBParser:
    """从 Annex-B 字节流中切分完整 NAL 单元。"""

    def __init__(self):
        self._buf = bytearray()

    def feed(self, data: bytes) -> list[bytes]:
        if not data:
            return []
        self._buf.extend(data)
        starts: list[int] = []
        i = 0
        buf = self._buf
        while i < len(buf) - 3:
            if buf[i : i + 4] == b"\x00\x00\x00\x01":
                starts.append(i)
                i += 4
            elif buf[i : i + 3] == b"\x00\x00\x01":
                starts.append(i)
                i += 3
            else:
                i += 1

        if len(starts) < 2:
            return []

        nals: list[bytes] = []
        for idx in range(len(starts) - 1):
            start = starts[idx]
            end = starts[idx + 1]
            sc = 4 if buf[start : start + 4] == b"\x00\x00\x00\x01" else 3
            payload = bytes(buf[start + sc : end])
            if payload:
                nals.append(payload)

        last = starts[-1]
        del buf[:last]
        return nals


async def _run_adb(*args: str, timeout: float = 30) -> tuple[int, bytes, bytes]:
    proc = await asyncio.create_subprocess_exec(
        "adb", *args,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except asyncio.TimeoutError:
        proc.kill()
        raise RuntimeError("adb 命令超时")
    return proc.returncode or 0, stdout or b"", stderr or b""


async def _query_wm_size(serial: str) -> tuple[int, int]:
    code, out, _ = await _run_adb("-s", serial, "shell", "wm", "size")
    if code == 0:
        m = re.search(r"(\d+)x(\d+)", out.decode(errors="ignore"))
        if m:
            return int(m.group(1)), int(m.group(2))
    return 1080, 1920


def _stream_size(native_w: int, native_h: int, max_size: int) -> tuple[int, int]:
    longer = max(native_w, native_h)
    if longer <= max_size:
        return native_w, native_h
    scale = max_size / longer
    return max(1, int(native_w * scale)), max(1, int(native_h * scale))


async def _cleanup_serial(serial: str):
    proc = _active.pop(serial, None)
    if proc and proc.returncode is None:
        proc.terminate()
        try:
            await asyncio.wait_for(proc.wait(), timeout=2)
        except asyncio.TimeoutError:
            proc.kill()
    await _run_adb("-s", serial, "shell", "pkill", "-f", "scrcpy.Server")


async def stream_scrcpy(websocket, serial: str):
    """通过 scrcpy-server raw H.264 流推送至 WebSocket；失败时抛出异常供上层 fallback。"""
    server_jar = find_scrcpy_server()
    if not server_jar:
        raise RuntimeError("未找到 scrcpy-server")

    port = random.randint(27183, 28183)

    await _cleanup_serial(serial)
    await asyncio.sleep(0.2)

    code, _, err = await _run_adb("-s", serial, "push", str(server_jar), REMOTE_JAR)
    if code != 0:
        raise RuntimeError(f"推送 scrcpy-server 失败: {err.decode(errors='ignore')[:200]}")

    shell = (
        f"CLASSPATH={REMOTE_JAR} app_process / com.genymobile.scrcpy.Server {SERVER_VERSION} "
        f"tunnel_forward=true audio=false control=false cleanup=false raw_stream=true "
        f"max_size={MAX_SIZE} max_fps={MAX_FPS} video_bit_rate={BIT_RATE}"
    )
    server_proc = await asyncio.create_subprocess_exec(
        "adb", "-s", serial, "shell", shell,
        stdout=asyncio.subprocess.DEVNULL,
        stderr=asyncio.subprocess.PIPE,
    )
    _active[serial] = server_proc

    await asyncio.sleep(0.8)

    code, _, err = await _run_adb("-s", serial, "forward", f"tcp:{port}", "localabstract:scrcpy")
    if code != 0:
        await _cleanup_serial(serial)
        raise RuntimeError(f"adb forward 失败: {err.decode(errors='ignore')[:200]}")

    reader: asyncio.StreamReader | None = None
    writer: asyncio.StreamWriter | None = None
    native_w, native_h = await _query_wm_size(serial)
    stream_w, stream_h = _stream_size(native_w, native_h, MAX_SIZE)

    parser = _AnnexBParser()
    sps = pps = None
    meta_sent = False
    pending_vcl: list[bytes] = []

    try:
        for _ in range(20):
            try:
                reader, writer = await asyncio.wait_for(
                    asyncio.open_connection("127.0.0.1", port), timeout=1.0
                )
                break
            except (OSError, asyncio.TimeoutError):
                if server_proc.returncode is not None:
                    err_msg = (await server_proc.stderr.read()).decode(errors="ignore") if server_proc.stderr else ""
                    raise RuntimeError(f"scrcpy-server 启动失败: {err_msg[:300]}")
                await asyncio.sleep(0.2)
        else:
            raise RuntimeError("连接 scrcpy-server 超时")

        async def flush_vcl(is_key: bool):
            nonlocal pending_vcl
            if not pending_vcl or not meta_sent:
                pending_vcl = []
                return
            if is_key and sps and pps:
                desc = _build_avcc(sps, pps)
                await websocket.send_bytes(bytes([1]) + desc)
            avcc = _to_avcc(pending_vcl)
            flags = 2 if is_key else 0
            await websocket.send_bytes(bytes([flags]) + avcc)
            pending_vcl = []

        while True:
            chunk = await reader.read(65536)
            if not chunk:
                raise ConnectionError("scrcpy 视频流已结束")

            for nal in parser.feed(chunk):
                ntype = _nal_type(nal)
                if ntype == 7:
                    sps = nal
                elif ntype == 8:
                    pps = nal
                elif ntype in (1, 5):
                    if pending_vcl:
                        await flush_vcl(_nal_type(pending_vcl[0]) == 5)
                    pending_vcl = [nal]
                elif pending_vcl and ntype in (6, 9):
                    pending_vcl.append(nal)

                if sps and pps and not meta_sent:
                    desc = _build_avcc(sps, pps)
                    desc_b64 = base64.b64encode(desc).decode("ascii")
                    await websocket.send_text(json.dumps({
                        "mode": "h264",
                        "codec": "avc1",
                        "width": stream_w,
                        "height": stream_h,
                        "native_width": native_w,
                        "native_height": native_h,
                        "description": desc_b64,
                    }))
                    meta_sent = True

            if pending_vcl and meta_sent:
                await flush_vcl(_nal_type(pending_vcl[0]) == 5)

    except WebSocketDisconnect:
        raise
    finally:
        if writer:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass
        await _run_adb("-s", serial, "forward", "--remove", f"tcp:{port}")
        await _cleanup_serial(serial)
