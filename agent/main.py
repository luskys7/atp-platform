"""设备 Agent - 注册设备、心跳上报、接收平台指令"""

import json
import logging
import os
import socket
import subprocess
import sys
import time
from pathlib import Path

import requests
import yaml

logging.basicConfig(level=logging.INFO, format="%(asctime)s [Agent] %(message)s")
logger = logging.getLogger("atp-agent")


def load_config() -> dict:
    config_path = Path(__file__).parent / "config" / "config.yaml"
    defaults = {
        "platform_url": "http://localhost:8080",
        "serial_number": "",
        "platform": "android",
        "agent_host": "",
        "agent_port": 9100,
        "heartbeat_sec": 2,
    }
    if config_path.exists():
        with open(config_path, encoding="utf-8") as f:
            defaults.update(yaml.safe_load(f) or {})
    defaults["platform_url"] = os.environ.get("PLATFORM_URL", defaults["platform_url"])
    defaults["platform"] = os.environ.get("PLATFORM", defaults["platform"])
    return defaults


def get_local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def detect_serial(platform: str) -> str:
    if platform == "ios":
        try:
            out = subprocess.check_output(["idevice_id", "-l"], text=True, timeout=5).strip()
            if out:
                return out.split("\n")[0]
        except Exception:
            pass
        return f"ios-{get_local_ip()}"

    try:
        out = subprocess.check_output(["adb", "get-serialno"], text=True, timeout=5).strip()
        if out and out != "unknown":
            return out
    except Exception:
        pass

    try:
        out = subprocess.check_output(["adb", "devices", "-l"], text=True, timeout=5)
        for line in out.splitlines():
            if "\tdevice" in line:
                return line.split("\t")[0]
    except Exception:
        pass
    return f"android-{get_local_ip()}"


def get_device_info(platform: str, serial: str) -> dict:
    info = {"os_version": "", "model": "", "width": 1080, "height": 2340}
    if platform == "android":
        try:
            info["os_version"] = subprocess.check_output(
                ["adb", "-s", serial, "shell", "getprop", "ro.build.version.release"],
                text=True, timeout=5).strip()
            info["model"] = subprocess.check_output(
                ["adb", "-s", serial, "shell", "getprop", "ro.product.model"],
                text=True, timeout=5).strip()
            size_out = subprocess.check_output(
                ["adb", "-s", serial, "shell", "wm", "size"], text=True, timeout=5)
            if "x" in size_out:
                parts = size_out.split(":")[-1].strip().split("x")
                info["width"], info["height"] = int(parts[0]), int(parts[1])
        except Exception as e:
            logger.warning("get device info failed: %s", e)
    else:
        info.update(os_version="17.0", model="iPhone", width=1170, height=2532)
    return info


def register(cfg: dict) -> bool:
    info = get_device_info(cfg["platform"], cfg["serial_number"])
    payload = {
        "serial_number": cfg["serial_number"],
        "name": info["model"] or cfg["serial_number"],
        "platform": cfg["platform"],
        "os_version": info["os_version"],
        "model": info["model"],
        "agent_host": cfg["agent_host"],
        "agent_port": cfg["agent_port"],
        "screen_width": info["width"],
        "screen_height": info["height"],
        "wda_port": 8100,
        "adb_port": 5037,
        "battery_level": 100,
    }
    url = f"{cfg['platform_url']}/api/v1/agent/devices/register"
    resp = requests.post(url, json=payload, timeout=10)
    if resp.status_code >= 400:
        logger.error("register failed: %s %s", resp.status_code, resp.text)
        return False
    logger.info("device registered: %s", cfg["serial_number"])
    return True


def heartbeat(cfg: dict):
    url = f"{cfg['platform_url']}/api/v1/agent/devices/heartbeat"
    payload = {"serial_number": cfg["serial_number"], "battery_level": 100}
    try:
        resp = requests.post(url, json=payload, timeout=5)
        if resp.status_code >= 400:
            logger.warning("heartbeat failed: %s", resp.status_code)
    except requests.RequestException as e:
        logger.warning("heartbeat error: %s", e)


def main():
    cfg = load_config()
    if not cfg["serial_number"]:
        cfg["serial_number"] = detect_serial(cfg["platform"])
    if not cfg["agent_host"]:
        cfg["agent_host"] = get_local_ip()

    logger.info("starting agent serial=%s platform=%s url=%s",
                cfg["serial_number"], cfg["platform"], cfg["platform_url"])

    if not register(cfg):
        logger.error("registration failed, retrying in 5s...")
        time.sleep(5)
        if not register(cfg):
            sys.exit(1)

    interval = cfg.get("heartbeat_sec", 2)
    while True:
        heartbeat(cfg)
        time.sleep(interval)


if __name__ == "__main__":
    main()
