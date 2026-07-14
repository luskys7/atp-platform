#!/usr/bin/env python3
import asyncio
import json
import urllib.request

from fastapi.testclient import TestClient

import sys
sys.path.insert(0, "executor")
from main import app  # noqa: E402


def get_token():
    login_body = json.dumps({"username": "admin", "password": "admin123"}).encode()
    login = json.loads(urllib.request.urlopen(
        urllib.request.Request(
            "http://localhost:8080/api/v1/auth/login",
            data=login_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
    ).read())
    token = login["data"]["token"]
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/devices/3/screen/start",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method="POST",
        data=b"{}",
    )
    start = json.loads(urllib.request.urlopen(req).read())
    return start["data"]["token"], start["data"]["serial_number"]


def test_testclient():
    t, s = get_token()
    client = TestClient(app)
    with client.websocket_connect(f"/ws/screen/{s}?token={t}") as ws:
        data = ws.receive_bytes()
        print("TestClient OK", len(data))


async def test_live(port=9002, path_prefix=""):
    import websockets

    t, s = get_token()
    uri = f"ws://127.0.0.1:{port}{path_prefix}/ws/screen/{s}?token={t}"
    async with websockets.connect(uri, open_timeout=8) as ws:
        data = await asyncio.wait_for(ws.recv(), timeout=10)
        print(f"live:{port} OK", len(data))


if __name__ == "__main__":
    asyncio.run(test_live(9002))
    try:
        asyncio.run(test_live(3000, path_prefix="/ws/executor"))
    except TypeError:
        pass
