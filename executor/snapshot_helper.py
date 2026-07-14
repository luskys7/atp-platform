"""失败现场快照：控件树、进程、内存"""

import json
import subprocess
from pathlib import Path
from typing import Optional


def capture_failure_snapshot(
    serial: str,
    app_package: Optional[str],
    task_id: int,
    execution_id: int,
    device_id: int,
    recordings_base: str,
) -> dict:
    snap: dict = {"ui_tree": "", "process_info": "", "memory_info": "", "screenshot_path": ""}
    rel = f"{task_id}/{device_id}/snapshots"
    local_dir = Path(recordings_base) / rel
    local_dir.mkdir(parents=True, exist_ok=True)

    remote_xml = f"/sdcard/atp_ui_{execution_id}.xml"
    local_xml = local_dir / f"{execution_id}_ui.xml"
    subprocess.run(
        ["adb", "-s", serial, "shell", "uiautomator", "dump", remote_xml],
        capture_output=True, text=True, timeout=20,
    )
    pull = subprocess.run(
        ["adb", "-s", serial, "pull", remote_xml, str(local_xml)],
        capture_output=True, text=True, timeout=20,
    )
    if pull.returncode == 0 and local_xml.exists():
        snap["ui_tree"] = local_xml.read_text(encoding="utf-8", errors="ignore")[:50000]
        snap["ui_tree_path"] = str(local_xml)

    if app_package:
        ps = subprocess.run(
            ["adb", "-s", serial, "shell", "ps", "-A"],
            capture_output=True, text=True, timeout=15,
        )
        lines = [ln for ln in (ps.stdout or "").splitlines() if app_package in ln]
        snap["process_info"] = "\n".join(lines[:20])

        mem = subprocess.run(
            ["adb", "-s", serial, "shell", "dumpsys", "meminfo", app_package],
            capture_output=True, text=True, timeout=20,
        )
        snap["memory_info"] = (mem.stdout or "")[:12000]

    remote_png = f"/sdcard/atp_snap_{execution_id}.png"
    local_png = local_dir / f"{execution_id}.png"
    subprocess.run(["adb", "-s", serial, "shell", "screencap", "-p", remote_png], timeout=15)
    sp = subprocess.run(
        ["adb", "-s", serial, "pull", remote_png, str(local_png)],
        capture_output=True, text=True, timeout=20,
    )
    if sp.returncode == 0 and local_png.exists():
        snap["screenshot_path"] = str(local_png)

    return snap


def emit_snapshot_marker(snapshot: dict) -> str:
    payload = {
        "ui_tree_len": len(snapshot.get("ui_tree") or ""),
        "process_info": snapshot.get("process_info", "")[:2000],
        "memory_info": snapshot.get("memory_info", "")[:4000],
        "screenshot_path": snapshot.get("screenshot_path", ""),
        "ui_tree_path": snapshot.get("ui_tree_path", ""),
        "ui_tree": (snapshot.get("ui_tree") or "")[:30000],
    }
    return "ATP_FAILURE_SNAPSHOT:" + json.dumps(payload, ensure_ascii=False)
