"""iOS WDA 自动部署：安装预签名 IPA、启动 Runner、端口转发与健康检查"""

import json
import logging
import os
import shutil
import subprocess
import time
from pathlib import Path
from typing import Any

from ios_helper import health_check

logger = logging.getLogger("wda_deploy")


def _which(name: str) -> str | None:
    return shutil.which(name)


def _run(cmd: list[str], timeout: int = 120) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, check=False)


def check_prerequisites() -> dict[str, Any]:
    tools = {
        "idevice_id": _which("idevice_id") is not None,
        "ideviceinstaller": _which("ideviceinstaller") is not None,
        "idevicedebug": _which("idevicedebug") is not None,
        "iproxy": _which("iproxy") is not None,
        "ios": _which("ios") is not None,
    }
    tools["ready"] = tools["idevice_id"] and (tools["ideviceinstaller"] or tools["ios"])
    return tools


def _list_installed(serial: str, bundle_id: str) -> bool:
    if _which("ideviceinstaller"):
        r = _run(["ideviceinstaller", "-u", serial, "-l"], timeout=60)
        return bundle_id in (r.stdout or "")
    if _which("ios"):
        r = _run(["ios", "list", "--udid", serial], timeout=60)
        return bundle_id in (r.stdout or "")
    return False


def install_wda_ipa(serial: str, ipa_path: str) -> list[str]:
    logs: list[str] = []
    path = Path(ipa_path)
    if not path.exists():
        raise FileNotFoundError(f"WDA IPA 不存在: {ipa_path}")
    if _which("ideviceinstaller"):
        r = _run(["ideviceinstaller", "-u", serial, "-i", str(path)], timeout=180)
        logs.append(r.stdout or r.stderr or "ideviceinstaller done")
        if r.returncode != 0:
            raise RuntimeError("ideviceinstaller 安装失败: " + (r.stderr or r.stdout or ""))
        return logs
    if _which("ios"):
        r = _run(["ios", "install", "--path", str(path), "--udid", serial], timeout=180)
        logs.append(r.stdout or r.stderr or "ios install done")
        if r.returncode != 0:
            raise RuntimeError("ios install 失败: " + (r.stderr or r.stdout or ""))
        return logs
    raise RuntimeError("未找到 ideviceinstaller 或 go-ios，无法安装 WDA")


def launch_wda_runner(serial: str, bundle_id: str) -> list[str]:
    logs: list[str] = []
    if _which("idevicedebug"):
        r = _run(["idevicedebug", "-u", serial, "run", bundle_id], timeout=15)
        logs.append("idevicedebug run: " + (r.stdout or r.stderr or "ok")[:200])
    elif _which("ios"):
        r = _run(["ios", "launch", bundle_id, "--udid", serial], timeout=30)
        logs.append("ios launch: " + (r.stdout or r.stderr or "ok")[:200])
        if r.returncode != 0:
            raise RuntimeError("ios launch 失败: " + (r.stderr or r.stdout or ""))
    else:
        logs.append("跳过 launch（无 idevicedebug/ios），请手动启动 WDA Runner")
    time.sleep(2)
    return logs


def start_iproxy(local_port: int, device_port: int, serial: str) -> subprocess.Popen | None:
    if not _which("iproxy"):
        return None
    proc = subprocess.Popen(
        ["iproxy", str(local_port), str(device_port), serial],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    time.sleep(1)
    return proc


def resign_wda_if_needed(ipa_path: str, config: dict) -> str:
    """macOS 下尝试重签；其他平台返回原 IPA（需预签名包）。"""
    p12 = config.get("p12_path") or ""
    profile = config.get("provisioning_profile_path") or ""
    team_id = config.get("team_id") or ""
    password = config.get("p12_password") or ""
    if not p12 or not Path(p12).exists():
        return ipa_path
    if os.name != "posix" or not _which("security"):
        logger.warning("非 macOS 或无 security 命令，使用预签名 IPA")
        return ipa_path
    out_ipa = str(Path(ipa_path).with_name(Path(ipa_path).stem + "_resigned.ipa"))
    # 简化流程：企业环境通常使用预签名 WDA；此处记录配置就绪
    logger.info("WDA resign config ready team_id=%s profile=%s", team_id, profile)
    return ipa_path if not Path(out_ipa).exists() else out_ipa


def deploy_wda(serial: str, config: dict | None = None) -> dict:
    cfg = config or {}
    ipa_path = cfg.get("wda_ipa_path") or "./data/wda/WebDriverAgentRunner.ipa"
    bundle_id = cfg.get("bundle_id") or "com.facebook.WebDriverAgentRunner.xctrunner"
    wda_port = int(cfg.get("wda_port") or 8100)
    local_port = int(cfg.get("local_port") or wda_port)
    auto_install = cfg.get("auto_install", True)

    logs: list[str] = []
    prereq = check_prerequisites()
    logs.append("tools: " + json.dumps(prereq, ensure_ascii=False))

    host = "127.0.0.1"
    existing = health_check(host, local_port)
    if existing.get("ok"):
        return {
            "success": True,
            "message": "WDA 已在运行",
            "wda_port": local_port,
            "health": existing,
            "logs": logs,
        }

    if not prereq.get("ready"):
        return {
            "success": False,
            "message": "缺少 libimobiledevice/go-ios 工具链，请安装 ideviceinstaller",
            "logs": logs,
            "prerequisites": prereq,
        }

    ipa_resolved = resign_wda_if_needed(ipa_path, cfg)
    installed = _list_installed(serial, bundle_id)
    logs.append(f"installed={installed} bundle={bundle_id}")

    if auto_install and not installed and Path(ipa_resolved).exists():
        logs.extend(install_wda_ipa(serial, ipa_resolved))
    elif auto_install and not Path(ipa_resolved).exists():
        logs.append(f"WDA IPA 未找到: {ipa_resolved}，跳过安装")

    logs.extend(launch_wda_runner(serial, bundle_id))
    proxy = start_iproxy(local_port, wda_port, serial)
    if proxy:
        logs.append(f"iproxy {local_port}->{wda_port} started")

    for _ in range(8):
        hc = health_check(host, local_port)
        if hc.get("ok"):
            return {
                "success": True,
                "message": "WDA 部署成功",
                "wda_port": local_port,
                "health": hc,
                "logs": logs,
            }
        time.sleep(1.5)

    return {
        "success": False,
        "message": "WDA 启动后健康检查未通过",
        "wda_port": local_port,
        "health": health_check(host, local_port),
        "logs": logs,
        "prerequisites": prereq,
    }
