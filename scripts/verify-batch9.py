#!/usr/bin/env python3
"""第九批：弱网 / 崩溃捕获 / 异常快照 冒烟验证"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))


class NetworkHelperTest(unittest.TestCase):
    @patch("network_helper._run")
    def test_apply_2g(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        from network_helper import apply_network_profile, reset_network_profile, network_delay_seconds

        msgs = apply_network_profile("emulator-5554", "2g")
        self.assertTrue(any("2g" in m for m in msgs))
        self.assertGreater(network_delay_seconds("2g"), 0)
        reset_network_profile("emulator-5554")
        self.assertEqual(network_delay_seconds("normal"), 0.0)


class CrashHelperTest(unittest.TestCase):
    @patch("crash_helper.subprocess.run")
    def test_capture_crash_structure(self, mock_run):
        mock_run.side_effect = [
            MagicMock(stdout="FATAL EXCEPTION: com.demo.app crashed\n", returncode=0),
            MagicMock(stdout="12345\n", returncode=0),
            MagicMock(stdout="", returncode=0),
        ]
        from crash_helper import capture_crash_logs

        data = capture_crash_logs("dev1", "com.demo.app")
        self.assertIn("fatal_lines", data)
        self.assertTrue(data["fatal_lines"])
        self.assertTrue(data["process_alive"])


class SnapshotHelperTest(unittest.TestCase):
    @patch("snapshot_helper.subprocess.run")
    def test_emit_marker(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        from snapshot_helper import capture_failure_snapshot, emit_snapshot_marker

        base = str(ROOT / "backend-java" / "data" / "recordings")
        Path(base).mkdir(parents=True, exist_ok=True)
        snap = capture_failure_snapshot("dev1", "com.demo.app", 1, 2, 3, base)
        snap["process_info"] = "u0_a123 com.demo.app"
        marker = emit_snapshot_marker(snap)
        self.assertTrue(marker.startswith("ATP_FAILURE_SNAPSHOT:"))
        payload = json.loads(marker.split(":", 1)[1])
        self.assertIn("process_info", payload)


class FailureSnapshotIngestTest(unittest.TestCase):
    def test_parse_markers(self):
        crash = '{"fatal_lines":["FATAL EXCEPTION"],"anr_lines":[]}'
        snap = json.dumps({"process_info": "demo", "memory_info": "heap 1mb"})
        logs = [
            f"ATP_CRASH_LOG:{crash}",
            f"ATP_FAILURE_SNAPSHOT:{snap}",
        ]
        crash_json = snap_json = None
        pat_c = re.compile(r"ATP_CRASH_LOG:(.+)")
        pat_s = re.compile(r"ATP_FAILURE_SNAPSHOT:(.+)")
        for msg in logs:
            m = pat_c.search(msg)
            if m:
                crash_json = m.group(1)
            m = pat_s.search(msg)
            if m:
                snap_json = m.group(1)
        self.assertIsNotNone(crash_json)
        self.assertIsNotNone(snap_json)
        self.assertIn("FATAL", crash_json)


def check_script_generation(token: str = "") -> tuple[bool, str]:
    """调用后端 visual preview API 校验脚本含弱网/崩溃步骤"""
    try:
        import urllib.request

        visual = {
            "version": 1,
            "human_delay": False,
            "wait_template": "weak_network",
            "steps": [
                {"type": "network_profile", "profile": "2g", "enabled": True},
                {"type": "wait", "seconds": 1, "enabled": True},
                {"type": "capture_crash", "enabled": True},
                {"type": "reset_network", "enabled": True},
            ],
        }
        body = json.dumps({"visual_json": json.dumps(visual)}).encode()
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        req = urllib.request.Request(
            "http://localhost:8080/api/v1/tasks/visual/preview",
            data=body,
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode())
        script = data.get("data", {}).get("script") or data.get("script", "")
        checks = [
            ("apply_network_profile", "apply_network_profile" in script),
            ("capture_crash_now", "capture_crash_now" in script),
            ("reset_network_profile", "reset_network_profile" in script),
            ("WAIT_MULTIPLIER via weak_network", "WAIT_MULTIPLIER" in script or "human_pause" in script),
        ]
        failed = [name for name, ok in checks if not ok]
        if failed:
            return False, "脚本缺少: " + ", ".join(failed)
        return True, f"脚本生成 OK ({len(script)} chars)"
    except Exception as e:
        return False, str(e)


def check_api_e2e() -> list[tuple[str, bool, str]]:
    results: list[tuple[str, bool, str]] = []
    try:
        import urllib.request

        # login
        login_body = json.dumps({"username": "admin", "password": "admin123"}).encode()
        req = urllib.request.Request(
            "http://localhost:8080/api/v1/auth/login",
            data=login_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            token = json.loads(resp.read().decode()).get("data", {}).get("token", "")
        if not token:
            results.append(("登录", False, "无 token"))
            return results
        results.append(("登录", True, "admin OK"))

        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

        ok, msg = check_script_generation(token)
        results.append(("Visual 脚本预览", ok, msg))

        # create task with weak network visual steps
        visual = {
            "version": 1,
            "wait_template": "weak_network",
            "steps": [
                {"type": "network_profile", "profile": "high_latency", "enabled": True},
                {"type": "capture_crash", "enabled": True},
                {"type": "reset_network", "enabled": True},
            ],
        }
        task_payload = json.dumps({
            "name": "Batch9-弱网崩溃验证",
            "platform": "android",
            "script_type": "visual",
            "script_content": json.dumps(visual),
            "app_package": "com.android.settings",
            "parallel_count": 1,
            "timeout_seconds": 120,
            "enable_recording": False,
        }).encode()
        req = urllib.request.Request(
            "http://localhost:8080/api/v1/tasks",
            data=task_payload,
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            task_id = json.loads(resp.read().decode()).get("data", {}).get("id")
        results.append(("创建弱网任务", bool(task_id), f"task_id={task_id}"))

        # failure-snapshots API shape (empty list OK)
        if task_id:
            req = urllib.request.Request(
                f"http://localhost:8080/api/v1/tasks/{task_id}/failure-snapshots",
                headers=headers,
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                snaps = json.loads(resp.read().decode()).get("data", [])
            results.append(("failure-snapshots API", True, f"count={len(snaps)}"))
    except Exception as e:
        results.append(("API E2E", False, str(e)))
    return results


def main():
    print("=" * 60)
    print("  Batch9 验证：弱网 / 崩溃捕获 / 异常快照")
    print("=" * 60)

    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    suite.addTests(loader.loadTestsFromTestCase(NetworkHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(CrashHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(SnapshotHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(FailureSnapshotIngestTest))
    runner = unittest.TextTestRunner(verbosity=2)
    unit_result = runner.run(suite)

    print("\n--- 后端 API（需 :8080 运行）---")
    token = ""
    try:
        import urllib.request
        login_body = json.dumps({"username": "admin", "password": "admin123"}).encode()
        req = urllib.request.Request(
            "http://localhost:8080/api/v1/auth/login",
            data=login_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            token = json.loads(resp.read().decode()).get("data", {}).get("token", "")
    except Exception:
        pass
    ok, msg = check_script_generation(token)
    print(f"[{'PASS' if ok else 'SKIP/FAIL'}] Visual preview: {msg}")

    api_results = check_api_e2e()
    for name, passed, detail in api_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")

    all_unit_ok = unit_result.wasSuccessful()
    api_ok = all(r[1] for r in api_results) if api_results else False
    print("\n" + "=" * 60)
    if all_unit_ok and ok and (api_ok or not api_results):
        print("全部通过")
    elif all_unit_ok and ok:
        print("单元测试 + 脚本生成：通过")
        if not api_ok:
            print("API E2E：未通过（请确认 backend 已启动并重试）")
    elif all_unit_ok:
        print("单元测试：通过；后端未启动，跳过 API 验证")
    else:
        print("存在失败项，请查看上方日志")
        sys.exit(1)
    if not ok:
        sys.exit(2)


if __name__ == "__main__":
    main()
