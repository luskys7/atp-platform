import subprocess
import logging
from typing import Optional, Tuple

logger = logging.getLogger(__name__)


def run_adb(serial: str, *args: str, timeout: int = 30) -> Tuple[int, str, str]:
    cmd = ["adb", "-s", serial] + list(args)
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout
        )
        return result.returncode, result.stdout, result.stderr
    except subprocess.TimeoutExpired:
        return -1, "", "adb command timeout"
    except FileNotFoundError:
        return -1, "", "adb not found in PATH"


def install_apk(serial: str, apk_path: str) -> bool:
    if not apk_path:
        return True
    code, stdout, stderr = run_adb(serial, "install", "-r", apk_path, timeout=120)
    if code != 0:
        logger.error("install failed: %s %s", stdout, stderr)
        return False
    return True


def uninstall_app(serial: str, package: str) -> bool:
    if not package:
        return True
    code, _, _ = run_adb(serial, "uninstall", package)
    return code == 0


def get_device_info(serial: str) -> dict:
    info = {"serial": serial}
    _, version, _ = run_adb(serial, "shell", "getprop", "ro.build.version.release")
    info["os_version"] = version.strip()
    _, model, _ = run_adb(serial, "shell", "getprop", "ro.product.model")
    info["model"] = model.strip()
    return info


def start_recording(serial: str, output_path: str) -> Optional[subprocess.Popen]:
    """Android screenrecord via adb (max 180s per segment, 25fps)."""
    cmd = [
        "adb", "-s", serial, "shell",
        "screenrecord", "--size", "720x1280", "--bit-rate", "4000000",
        f"/sdcard/atp_rec_{output_path.split('/')[-1]}"
    ]
    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return proc
    except Exception as e:
        logger.error("start recording failed: %s", e)
        return None


def stop_recording(serial: str, remote_name: str, local_path: str) -> bool:
    run_adb(serial, "shell", "pkill", "-2", "screenrecord")
    import time
    time.sleep(1)
    code, _, _ = run_adb(serial, "pull", f"/sdcard/{remote_name}", local_path, timeout=60)
    run_adb(serial, "shell", "rm", f"/sdcard/{remote_name}")
    return code == 0
