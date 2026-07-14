"""iOS WebDriverAgent HTTP 辅助（直连 WDA，无需 Appium Server）"""
import json
import urllib.error
import urllib.request


def wda_base(agent_host: str, wda_port: int = 8100) -> str:
    host = (agent_host or "127.0.0.1").strip()
    return f"http://{host}:{wda_port}"


def health_check(agent_host: str, wda_port: int = 8100) -> dict:
    url = wda_base(agent_host, wda_port) + "/status"
    try:
        with urllib.request.urlopen(url, timeout=5) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
            data = json.loads(body) if body.strip().startswith("{") else {}
            return {"ok": True, "status": data}
    except Exception as e:
        return {"ok": False, "error": str(e)}


def wda_tap(agent_host: str, wda_port: int, x: int, y: int) -> None:
    url = wda_base(agent_host, wda_port) + "/wda/tap/0"
    payload = json.dumps({"x": int(x), "y": int(y)}).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=15):
        pass


def wda_launch_bundle(agent_host: str, wda_port: int, bundle_id: str) -> None:
    url = wda_base(agent_host, wda_port) + "/session"
    payload = json.dumps({
        "capabilities": {
            "bundleId": bundle_id,
            "shouldWaitForQuiescence": False,
        }
    }).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30):
        pass
