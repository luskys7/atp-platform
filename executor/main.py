"""Python 执行器 - 接收 Java 调度中心下发的自动化任务，执行 ADB/Appium 脚本"""

import asyncio
import hashlib
import json
import logging
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, Optional

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query
from pydantic import BaseModel, Field

import screen_stream
import scrcpy_stream
from execution_context import ExecutionContext, grant_android_permissions, parse_calibration_json, revoke_android_permissions

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("atp-executor")

from concurrent.futures import ThreadPoolExecutor

_adb_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="adb-heavy")
_tap_executor = ThreadPoolExecutor(max_workers=8, thread_name_prefix="adb-tap")
_inspect_executor = ThreadPoolExecutor(max_workers=4, thread_name_prefix="adb-inspect")

app = FastAPI(title="TestFlow Executor", version="1.0.0")

# 与 backend-java/data/recordings 对齐，可通过环境变量覆盖
DEFAULT_RECORDINGS = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "backend-java", "data", "recordings")
)
RECORDINGS_BASE = os.environ.get("ATP_RECORDINGS_PATH", DEFAULT_RECORDINGS)
EXECUTOR_DIR = os.path.dirname(os.path.abspath(__file__))

BACKEND_URL = os.environ.get("ATP_BACKEND_URL", "http://localhost:8080")
screen_stream.set_verify_url(f"{BACKEND_URL}/api/v1/screen/verify")


class ExecuteRequest(BaseModel):
    task_id: int
    execution_id: int
    device_id: int
    serial_number: str
    platform: str
    script_type: str
    script_content: str
    app_package: Optional[str] = None
    app_path: Optional[str] = None
    enable_recording: bool = True
    agent_host: Optional[str] = None
    agent_port: Optional[int] = None
    wda_port: Optional[int] = 8100
    adb_port: Optional[int] = 5037
    timeout_seconds: int = 3600
    resolved_controls: Optional[str] = None
    variables_json: Optional[str] = None
    calibration_json: Optional[str] = None
    auto_grant_permissions: bool = True
    auto_revoke_permissions: bool = True
    auto_collect_controls: bool = True
    execution_isolation: bool = True
    operator_id: Optional[int] = None
    operator_label: Optional[str] = None
    assert_policy_json: Optional[str] = None


class LogEntry(BaseModel):
    log_type: str
    level: str
    message: str


class ExecuteResponse(BaseModel):
    success: bool
    summary: str = ""
    error_message: str = ""
    recording_path: Optional[str] = None
    watermark_hash: Optional[str] = None
    file_size: Optional[int] = None
    operator_label: Optional[str] = None
    logs: list[LogEntry] = Field(default_factory=list)


class DeviceAppRequest(BaseModel):
    serial_number: str
    platform: str
    app_path: Optional[str] = None
    app_package: Optional[str] = None


class TapRequest(BaseModel):
    serial_number: str
    x: int
    y: int
    platform: str = "android"
    agent_host: Optional[str] = None
    wda_port: int = 8100


class WdaStatusRequest(BaseModel):
    agent_host: str = "127.0.0.1"
    wda_port: int = 8100


class InspectPointRequest(BaseModel):
    serial_number: str
    platform: str = "android"
    x: int
    y: int
    display_width: int = 0
    display_height: int = 0
    blocking: bool = False
    after_tap: bool = False
    agent_host: Optional[str] = None
    wda_port: int = 8100


class SwitchContextRequest(BaseModel):
    serial_number: str
    target: str = "auto"


class PageReadyRequest(BaseModel):
    serial_number: str
    timeout: float = 10.0


class WarmUiCacheRequest(BaseModel):
    serial_number: str
    platform: str = "android"
    blocking: bool = False


class ValidateLocatorRequest(BaseModel):
    serial_number: str
    platform: str = "android"
    locators: dict = {}
    locator_chain: list = Field(default_factory=list)


class WdaDeployRequest(BaseModel):
    serial_number: str
    wda_ipa_path: Optional[str] = None
    bundle_id: Optional[str] = None
    wda_port: int = 8100
    local_port: Optional[int] = None
    auto_install: bool = True
    p12_path: Optional[str] = None
    p12_password: Optional[str] = None
    provisioning_profile_path: Optional[str] = None
    team_id: Optional[str] = None


class SwipeRequest(BaseModel):
    serial_number: str
    x1: int
    y1: int
    x2: int
    y2: int
    duration_ms: int = 300


class InputTextRequest(BaseModel):
    serial_number: str
    text: str
    focus_x: int | None = None
    focus_y: int | None = None


class SystemKeyRequest(BaseModel):
    serial_number: str
    key: str


@app.get("/health")
def health():
    return {"status": "ok", "service": "atp-python-executor", "recordings_base": RECORDINGS_BASE}


@app.get("/api/v1/device/adb-list")
def device_adb_list():
    from adb_discovery import list_usb_devices
    return list_usb_devices()


@app.post("/api/v1/execute", response_model=ExecuteResponse)
def execute(req: ExecuteRequest):
    logger.info("execute task=%s exec=%s device=%s platform=%s",
                req.task_id, req.execution_id, req.serial_number, req.platform)
    logs: list[LogEntry] = []
    ctx = ExecutionContext(req.task_id, req.execution_id, req.execution_isolation)
    try:
        if req.platform == "android" and req.auto_grant_permissions:
            for msg in grant_android_permissions(req.serial_number, req.app_package):
                logs.append(LogEntry(log_type="platform", level="info", message=f"权限: {msg}"))

        if req.platform == "android":
            try:
                from interference_helper import enable_focus_mode
                for msg in enable_focus_mode(req.serial_number):
                    logs.append(LogEntry(log_type="platform", level="info", message=f"干扰降噪: {msg}"))
            except Exception as ie:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"干扰降噪跳过: {ie}"))
            try:
                from keepalive_helper import enable_keep_awake
                for msg in enable_keep_awake(req.serial_number):
                    logs.append(LogEntry(log_type="platform", level="info", message=f"保活: {msg}"))
            except Exception as ke:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"保活跳过: {ke}"))

        if req.platform == "android":
            try:
                from popup_helper import dismiss_android_popups
                for msg in dismiss_android_popups(req.serial_number):
                    logs.append(LogEntry(log_type="platform", level="info", message=f"弹窗拦截: {msg}"))
            except Exception as pe:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"弹窗拦截跳过: {pe}"))

        if req.app_path and os.path.exists(req.app_path):
            _install_app(req)
            logs.append(LogEntry(log_type="platform", level="info", message=f"已安装应用: {req.app_path}"))

        logcat_proc = _start_logcat(req) if req.platform == "android" else None

        if req.enable_recording:
            _start_recording(req)
            logs.append(LogEntry(log_type="platform", level="info", message="录屏已启动"))
            logs.append(LogEntry(
                log_type="platform", level="info",
                message=f"ATP_RECORDING_BEGIN task_id={req.task_id} execution_id={req.execution_id}",
            ))

        if req.platform == "android" and req.app_package:
            _adb_cmd(req, "monkey", "-p", req.app_package, "-c", "android.intent.category.LAUNCHER", "1")
            time.sleep(2)
            logs.append(LogEntry(log_type="platform", level="info", message=f"已启动目标应用: {req.app_package}"))

        result = _run_script(req, ctx)
        logs.append(LogEntry(log_type="script", level="info", message=result[:2000]))

        if req.platform == "android" and req.auto_collect_controls and req.app_package:
            try:
                from control_collector import collect_page_controls
                controls = collect_page_controls(req.serial_number, req.app_package)
                if controls:
                    payload = json.dumps(controls, ensure_ascii=False)
                    logs.append(LogEntry(log_type="platform", level="info",
                                         message="ATP_CONTROLS_JSON:" + payload))
                    logs.append(LogEntry(log_type="platform", level="info",
                                         message=f"自动采集控件 {len(controls)} 个"))
            except Exception as ce:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"控件采集跳过: {ce}"))

        if logcat_proc:
            logs.extend(_collect_logcat(logcat_proc))

        recording_path = None
        watermark_hash = None
        file_size = None
        if req.enable_recording:
            recording_path, watermark_hash, file_size = _stop_recording(req)
            if recording_path:
                key = f"{req.task_id}_{req.execution_id}"
                started = _recording_started_at.pop(key, None)
                duration_ms = max(0, int((time.time() - started) * 1000)) if started else 0
                logs.append(LogEntry(log_type="platform", level="info",
                                     message=f"录屏已保存: {recording_path}"))
                logs.append(LogEntry(
                    log_type="platform", level="info",
                    message=f"ATP_RECORDING_END duration_ms={duration_ms} file={recording_path}",
                ))

        return ExecuteResponse(
            success=True,
            summary=result,
            recording_path=recording_path,
            watermark_hash=watermark_hash,
            file_size=file_size,
            operator_label=req.operator_label,
            logs=logs,
        )
    except Exception as e:
        logger.exception("execution failed")
        logs.append(LogEntry(log_type="script", level="error", message=str(e)))
        if req.platform == "android":
            shot = _capture_screenshot(req)
            if shot:
                logs.append(LogEntry(log_type="platform", level="info", message=f"失败截图: {shot}"))
            try:
                from crash_helper import capture_crash_logs
                import json as _json
                crash = capture_crash_logs(req.serial_number, req.app_package)
                logs.append(LogEntry(log_type="platform", level="error",
                                     message="ATP_CRASH_LOG:" + _json.dumps(crash, ensure_ascii=False)[:12000]))
            except Exception as ce:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"崩溃采集跳过: {ce}"))
            try:
                from snapshot_helper import capture_failure_snapshot, emit_snapshot_marker
                snap = capture_failure_snapshot(
                    req.serial_number, req.app_package,
                    req.task_id, req.execution_id, req.device_id, RECORDINGS_BASE,
                )
                logs.append(LogEntry(log_type="platform", level="info", message=emit_snapshot_marker(snap)))
            except Exception as se:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"快照采集跳过: {se}"))
        if req.enable_recording:
            try:
                _stop_recording(req)
            except Exception:
                pass
        return ExecuteResponse(success=False, error_message=str(e), logs=logs)
    finally:
        if req.platform == "android":
            try:
                from interference_helper import disable_focus_mode
                disable_focus_mode(req.serial_number)
            except Exception:
                pass
            try:
                from keepalive_helper import disable_keep_awake
                disable_keep_awake(req.serial_number)
            except Exception:
                pass
        if req.platform == "android" and req.auto_revoke_permissions and req.app_package:
            try:
                for msg in revoke_android_permissions(req.serial_number, req.app_package):
                    logs.append(LogEntry(log_type="platform", level="info", message=f"权限回收: {msg}"))
            except Exception as re:
                logs.append(LogEntry(log_type="platform", level="warn", message=f"权限回收跳过: {re}"))
        ctx.cleanup()


@app.post("/api/v1/device/install")
def install_app(req: DeviceAppRequest):
    if req.platform != "android" or not req.app_path:
        return {"success": False, "message": "Android 需提供 app_path"}
    cmd = ["adb", "-s", req.serial_number, "install", "-r", req.app_path]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        return {"success": False, "message": result.stderr or "安装失败"}
    return {"success": True, "message": "安装成功"}


@app.post("/api/v1/device/uninstall")
def uninstall_app(req: DeviceAppRequest):
    if not req.app_package:
        return {"success": False, "message": "需提供 app_package"}
    if req.platform == "android":
        cmd = ["adb", "-s", req.serial_number, "uninstall", req.app_package]
    else:
        return {"success": False, "message": "iOS 卸载需通过 WDA/Agent 扩展"}
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if result.returncode != 0:
        return {"success": False, "message": result.stderr or "卸载失败"}
    return {"success": True, "message": "卸载成功"}


class AiLocateRequest(BaseModel):
    serial_number: str
    platform: str = "android"
    query: str
    app_package: Optional[str] = ""


class AiLocateResponse(BaseModel):
    success: bool
    locator_type: str = ""
    locator_value: str = ""
    confidence: float = 0
    strategy: str = ""
    error_message: str = ""


@app.post("/api/v1/ai/locate", response_model=AiLocateResponse)
def ai_locate(req: AiLocateRequest):
    try:
        from ai_locator import locate
        result = locate(req.serial_number, req.platform, req.query, req.app_package or "")
        return AiLocateResponse(**result)
    except Exception as e:
        logger.exception("ai locate failed")
        return AiLocateResponse(success=False, error_message=str(e))


@app.websocket("/ws/screen/{serial}")
async def ws_screen(websocket: WebSocket, serial: str, token: str = Query(...), mode: str = Query("auto")):
    if not screen_stream.verify_token(token, serial):
        await websocket.close(code=4001, reason="invalid token")
        return
    await websocket.accept()
    env_mode = os.environ.get("ATP_SCREEN_MODE", "auto").lower()
    stream_mode = (mode or env_mode or "auto").lower()
    use_scrcpy = stream_mode in ("auto", "scrcpy") and scrcpy_stream.is_available()
    if stream_mode == "jpeg":
        use_scrcpy = False
    if use_scrcpy:
        logger.info("screen stream scrcpy serial=%s", serial)
        try:
            await scrcpy_stream.stream_scrcpy(websocket, serial)
            return
        except Exception as e:
            logger.warning("scrcpy stream failed serial=%s: %s, fallback adb", serial, e)
            if stream_mode == "scrcpy":
                await websocket.close(code=1011, reason=str(e)[:120])
                return
            try:
                await websocket.send_text('{"mode":"jpeg","fallback":true}')
            except Exception:
                pass
    logger.info("screen stream adb serial=%s", serial)
    try:
        await screen_stream.stream_screen(websocket, serial)
    except WebSocketDisconnect:
        logger.info("screen stream disconnected serial=%s", serial)
    except Exception as e:
        logger.exception("screen stream error serial=%s", serial)
        try:
            await websocket.close(code=1011, reason=str(e)[:120])
        except Exception:
            pass


@app.post("/api/v1/device/tap")
async def device_tap(req: TapRequest):
    loop = asyncio.get_running_loop()
    try:
        if req.platform == "ios":
            from ios_helper import wda_tap
            await loop.run_in_executor(
                _adb_executor,
                lambda: wda_tap(req.agent_host or "127.0.0.1", req.wda_port, req.x, req.y),
            )
        else:
            await loop.run_in_executor(
                _tap_executor,
                lambda: screen_stream.adb_tap(req.serial_number, req.x, req.y),
            )
    except Exception as e:
        from fastapi import HTTPException
        raise HTTPException(status_code=502, detail=str(e))
    return {"success": True}


@app.post("/api/v1/device/wda-status")
def device_wda_status(req: WdaStatusRequest):
    from ios_helper import health_check
    return health_check(req.agent_host, req.wda_port)


@app.post("/api/v1/device/inspect-point")
async def device_inspect_point(req: InspectPointRequest):
    from record_helper import inspect_point
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(
        _inspect_executor,
        lambda: inspect_point(
            req.serial_number, req.x, req.y, req.platform,
            display_width=req.display_width or 0,
            display_height=req.display_height or 0,
            blocking=req.blocking,
            agent_host=req.agent_host or "",
            wda_port=req.wda_port or 8100,
        ),
    )


@app.post("/api/v1/device/switch-context")
def device_switch_context(req: SwitchContextRequest):
    from webview_helper import switch_context
    logs = switch_context(req.serial_number, req.target or "auto")
    return {"ok": True, "logs": logs}


@app.post("/api/v1/device/wait-page-ready")
def device_wait_page_ready(req: PageReadyRequest):
    from record_helper import wait_page_ready
    return wait_page_ready(req.serial_number, timeout=req.timeout)


@app.post("/api/v1/device/warm-ui-cache")
def device_warm_ui_cache(req: WarmUiCacheRequest):
    from record_helper import warm_ui_cache
    if req.platform != "android":
        return {"ok": False, "message": "unsupported platform"}
    return warm_ui_cache(req.serial_number, blocking=req.blocking)


@app.post("/api/v1/device/validate-locator")
async def device_validate_locator(req: ValidateLocatorRequest):
    from record_helper import validate_locators_on_screen
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(
        _adb_executor,
        lambda: validate_locators_on_screen(
            req.serial_number, req.locators or {}, req.locator_chain or None
        ),
    )


@app.post("/api/v1/device/wda-deploy")
def device_wda_deploy(req: WdaDeployRequest):
    from wda_deploy_helper import deploy_wda
    cfg = {
        "wda_ipa_path": req.wda_ipa_path or "./data/wda/WebDriverAgentRunner.ipa",
        "bundle_id": req.bundle_id or "com.facebook.WebDriverAgentRunner.xctrunner",
        "wda_port": req.wda_port,
        "local_port": req.local_port or req.wda_port,
        "auto_install": req.auto_install,
        "p12_path": req.p12_path,
        "p12_password": req.p12_password,
        "provisioning_profile_path": req.provisioning_profile_path,
        "team_id": req.team_id,
    }
    return deploy_wda(req.serial_number, cfg)


@app.post("/api/v1/device/swipe")
def device_swipe(req: SwipeRequest):
    screen_stream.adb_swipe(req.serial_number, req.x1, req.y1, req.x2, req.y2, req.duration_ms)
    return {"success": True}


@app.post("/api/v1/device/input-text")
def device_input_text(req: InputTextRequest):
    from text_input_helper import adb_input_text as do_input
    ok = do_input(req.serial_number, req.text, req.focus_x, req.focus_y)
    if not ok:
        from fastapi import HTTPException
        raise HTTPException(
            status_code=400,
            detail="文本未写入，请点击搜索框/输入框中央后再发送",
        )
    return {"success": True}


@app.post("/api/v1/device/system-key")
def device_system_key(req: SystemKeyRequest):
    from system_ops import press_system_key
    press_system_key(req.serial_number, req.key)
    return {"success": True}


def _adb_cmd(req: ExecuteRequest, *args: str) -> subprocess.CompletedProcess:
    cmd = ["adb", "-s", req.serial_number, *args]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=60)


def _install_app(req: ExecuteRequest):
    if req.platform == "android" and req.app_path:
        _adb_cmd(req, "install", "-r", req.app_path)


def _run_script(req: ExecuteRequest, ctx: ExecutionContext) -> str:
    if req.script_type == "python":
        return _run_python_script(req, ctx)
    elif req.script_type == "appium":
        return _run_appium_script(req, ctx)
    return "不支持的脚本类型: " + req.script_type


def _run_python_script(req: ExecuteRequest, ctx: ExecutionContext) -> str:
    with tempfile.NamedTemporaryFile(mode="w", suffix=".py", delete=False, encoding="utf-8") as f:
        f.write(req.script_content)
        script_path = f.name

    env = os.environ.copy()
    env["ATP_DEVICE_SERIAL"] = req.serial_number
    env["ATP_PLATFORM"] = req.platform
    env["ATP_APP_PACKAGE"] = req.app_package or ""
    env["ATP_CONTROLS_JSON"] = req.resolved_controls or '{"elements":{}}'
    env["ATP_VARS_JSON"] = req.variables_json or "{}"
    env["ATP_CALIBRATION_JSON"] = req.calibration_json or json.dumps(parse_calibration_json(None))
    if req.assert_policy_json:
        env["ATP_ASSERT_POLICY_JSON"] = req.assert_policy_json
    env.update(ctx.env_overlay())
    env["PYTHONPATH"] = EXECUTOR_DIR + os.pathsep + env.get("PYTHONPATH", "")
    rec_key = f"{req.task_id}_{req.execution_id}"
    if rec_key in _recording_started_at:
        env["ATP_RECORDING_STARTED_AT"] = str(_recording_started_at[rec_key])

    try:
        result = subprocess.run(
            [sys.executable, script_path],
            capture_output=True, text=True,
            timeout=req.timeout_seconds,
            env=env,
        )
        if result.returncode != 0:
            combined = (result.stdout or "") + ("\n" if result.stdout and result.stderr else "") + (result.stderr or "")
            raise RuntimeError(combined.strip() or "脚本执行失败")
        return result.stdout or "Python 脚本执行成功"
    finally:
        os.unlink(script_path)


def _run_appium_script(req: ExecuteRequest, ctx: ExecutionContext) -> str:
    wrapper = f"""
from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.options.ios import XCUITestOptions
import os

platform = os.environ.get("ATP_PLATFORM", "android")
serial = os.environ.get("ATP_DEVICE_SERIAL", "")

if platform == "android":
    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.udid = serial
    options.app_package = os.environ.get("ATP_APP_PACKAGE", "")
    driver = webdriver.Remote("http://localhost:4723", options=options)
else:
    options = XCUITestOptions()
    options.platform_name = "iOS"
    options.udid = serial
    driver = webdriver.Remote("http://localhost:4723", options=options)

try:
{chr(10).join("    " + line for line in req.script_content.splitlines())}
finally:
    driver.quit()
"""
    req.script_content = wrapper
    req.script_type = "python"
    return _run_python_script(req, ctx)


def _capture_screenshot(req: ExecuteRequest) -> Optional[str]:
    if req.platform != "android":
        return None
    rel_dir = f"{req.task_id}/{req.device_id}"
    local_dir = Path(RECORDINGS_BASE) / rel_dir / "screenshots"
    local_dir.mkdir(parents=True, exist_ok=True)
    local_path = local_dir / f"{req.execution_id}_fail.png"
    remote = f"/sdcard/atp_shot_{req.execution_id}.png"
    subprocess.run(["adb", "-s", req.serial_number, "shell", "screencap", "-p", remote],
                   capture_output=True, timeout=30)
    result = subprocess.run(["adb", "-s", req.serial_number, "pull", remote, str(local_path)],
                            capture_output=True, timeout=60)
    subprocess.run(["adb", "-s", req.serial_number, "shell", "rm", remote], capture_output=True)
    if result.returncode == 0 and local_path.exists():
        return str(local_path)
    return None


_recording_procs: dict[str, subprocess.Popen] = {}
_recording_remote: dict[str, str] = {}
_recording_started_at: dict[str, float] = {}


def _start_recording(req: ExecuteRequest):
    key = f"{req.task_id}_{req.execution_id}"
    if req.platform == "android":
        remote_path = f"/sdcard/atp_rec_{req.execution_id}.mp4"
        proc = subprocess.Popen(
            ["adb", "-s", req.serial_number, "shell", "screenrecord", remote_path],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        _recording_procs[key] = proc
        _recording_remote[key] = remote_path
        _recording_started_at[key] = time.time()
        time.sleep(1)
    elif req.platform == "ios":
        logger.info("iOS 录屏需 WDA/Agent 扩展，当前跳过设备端录屏")


def _stop_recording(req: ExecuteRequest) -> tuple[Optional[str], Optional[str], Optional[int]]:
    key = f"{req.task_id}_{req.execution_id}"
    proc = _recording_procs.pop(key, None)
    remote = _recording_remote.pop(key, None)
    _recording_started_at.pop(key, None)
    if proc:
        proc.terminate()
        time.sleep(2)

    if req.platform != "android" or not remote:
        return None, None, None

    rel_dir = f"{req.task_id}/{req.device_id}"
    local_dir = Path(RECORDINGS_BASE) / rel_dir
    local_dir.mkdir(parents=True, exist_ok=True)
    local_path = local_dir / f"{req.execution_id}.mp4"

    subprocess.run(["adb", "-s", req.serial_number, "pull", remote, str(local_path)],
                   capture_output=True, timeout=120)
    subprocess.run(["adb", "-s", req.serial_number, "shell", "rm", remote], capture_output=True)

    if not local_path.exists():
        return None, None, None

    file_size = local_path.stat().st_size
    watermark = _compute_watermark(req.task_id, req.execution_id, req.operator_label, local_path)
    _write_recording_meta(local_path, req)
    object_path = f"{rel_dir}/{req.execution_id}.mp4"
    return object_path, watermark, file_size


def _write_recording_meta(local_path: Path, req: ExecuteRequest):
    meta = {
        "task_id": req.task_id,
        "execution_id": req.execution_id,
        "device_id": req.device_id,
        "operator_id": req.operator_id,
        "operator_label": req.operator_label or "",
        "platform": req.platform,
    }
    meta_path = local_path.with_suffix(".meta.json")
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")


def _compute_watermark(task_id: int, execution_id: int, operator_label: Optional[str], file_path: Path) -> str:
    h = hashlib.sha256()
    label = operator_label or ""
    h.update(f"{task_id}:{execution_id}:{label}".encode())
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _start_logcat(req: ExecuteRequest) -> subprocess.Popen:
    return subprocess.Popen(
        ["adb", "-s", req.serial_number, "logcat", "-d", "-t", "200"],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True,
    )


def _collect_logcat(proc: subprocess.Popen) -> list[LogEntry]:
    logs = []
    try:
        stdout, _ = proc.communicate(timeout=30)
        if stdout:
            for line in stdout.strip().splitlines()[-50:]:
                level = "error" if any(x in line for x in (" E ", " FATAL", "AndroidRuntime")) else "info"
                logs.append(LogEntry(log_type="logcat", level=level, message=line[:500]))
    except Exception:
        proc.kill()
    return logs


if __name__ == "__main__":
    port = int(os.environ.get("EXECUTOR_PORT", "9002"))
    uvicorn.run(app, host="0.0.0.0", port=port, ws="websockets")
