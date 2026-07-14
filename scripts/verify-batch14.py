#!/usr/bin/env python3
"""第十四批 P0 残留：音量断言 / 录屏增强 / WDA 部署 冒烟验证"""
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))


class VolumeAssertTest(unittest.TestCase):
    @patch("system_ops._run_adb")
    @patch("system_ops.press_system_key")
    def test_assert_volume_change_up(self, mock_press, mock_adb):
        from system_ops import assert_volume_change

        mock_adb.side_effect = [
            MagicMock(returncode=0, stdout="5"),
            MagicMock(returncode=0, stdout="7"),
        ]
        assert_volume_change("dev1", "up", "music")
        mock_press.assert_called_once()

    @patch("system_ops._run_adb")
    def test_assert_volume_exact(self, mock_adb):
        from system_ops import assert_volume

        mock_adb.return_value = MagicMock(returncode=0, stdout="volume is 8")
        assert_volume("dev1", 8, "music", 1)


class RecordHelperTest(unittest.TestCase):
    @patch("record_helper.dump_ui")
    @patch("record_helper.detect_context")
    def test_inspect_point_uiautomator(self, mock_ctx, mock_dump):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node text="登录" resource-id="com.app:id/login" bounds="[100,200][300,260]" clickable="true"/>
        </hierarchy>
        """
        r = inspect_point("dev1", 150, 230)
        self.assertEqual(r["source"], "uiautomator")
        self.assertEqual(r["element_name"], "登录")
        self.assertEqual(r["text"], "登录")

    @patch("record_helper.dump_ui")
    @patch("record_helper.detect_context")
    def test_inspect_point_webview(self, mock_ctx, mock_dump):
        from record_helper import inspect_point

        mock_ctx.return_value = "webview"
        mock_dump.return_value = "<hierarchy><node class='android.webkit.WebView' bounds='[0,0][1080,1920]'/></hierarchy>"
        r = inspect_point("dev1", 100, 100)
        self.assertTrue(r["needs_context_switch"])
        self.assertEqual(r["context"], "webview")


class WdaDeployTest(unittest.TestCase):
    @patch("wda_deploy_helper.health_check", return_value={"ok": True, "status": {}})
    def test_deploy_already_running(self, mock_health):
        from wda_deploy_helper import deploy_wda

        r = deploy_wda("ios-udid", {})
        self.assertTrue(r["success"])
        mock_health.assert_called_once()


def static_checks():
    checks = []
    gen = (ROOT / "backend-java/src/main/java/com/atp/platform/service/VisualScriptGenerator.java").read_text(encoding="utf-8")
    checks.append(("assert_volume 脚本生成", "assert_volume_level" in gen and "assert_volume_changed" in gen, "VisualScriptGenerator"))
    rec = (ROOT / "backend-java/src/main/java/com/atp/platform/service/OperationRecordService.java").read_text(encoding="utf-8")
    checks.append(("录制 enrichRecordedSteps", "enrichRecordedSteps" in rec and "inspectPoint" in rec, "OperationRecordService"))
    checks.append(("IosWdaService", (ROOT / "backend-java/src/main/java/com/atp/platform/service/IosWdaService.java").exists(), "IosWdaService.java"))
    main_py = (ROOT / "executor/main.py").read_text(encoding="utf-8")
    checks.append(("inspect-point API", "inspect-point" in main_py, "main.py"))
    checks.append(("wda-deploy API", "wda-deploy" in main_py, "main.py"))
    return checks


def api_checks():
    import urllib.request

    results = []
    try:
        urllib.request.urlopen("http://localhost:8080/api/v1/health", timeout=3)
    except Exception:
        results.append(("Backend :8080", False, "未启动，跳过 API"))
        return results

    login_body = json.dumps({"username": "admin", "password": "admin123"}).encode()
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/auth/login",
        data=login_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    token = json.loads(urllib.request.urlopen(req, timeout=5).read())["data"]["token"]

    preview_body = json.dumps({
        "visual_json": json.dumps({
            "version": 1,
            "steps": [
                {"type": "assert_volume", "expected": 8, "stream": "music", "tolerance": 1, "enabled": True},
                {"type": "assert_volume_change", "direction": "up", "stream": "music", "key": "volume_up", "enabled": True},
            ],
        }),
    }).encode()
    prev_req = urllib.request.Request(
        "http://localhost:8080/api/v1/tasks/visual/preview",
        data=preview_body,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
        method="POST",
    )
    script = json.loads(urllib.request.urlopen(prev_req, timeout=10).read())["data"]["script"]
    ok = "assert_volume_level" in script and "assert_volume_changed" in script
    results.append(("音量断言脚本预览", ok, f"{len(script)} chars"))

    try:
        exec_body = json.dumps({"serial_number": "test", "platform": "android", "x": 100, "y": 200}).encode()
        exec_req = urllib.request.Request(
            "http://localhost:9002/api/v1/device/inspect-point",
            data=exec_body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        urllib.request.urlopen(exec_req, timeout=5)
        results.append(("inspect-point 执行器", True, "endpoint ok"))
    except Exception as e:
        results.append(("inspect-point 执行器", False, str(e)[:80]))

    return results


def main():
    print("  Batch14 验证：P0 残留（音量断言 / 录屏增强 / WDA）")
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    suite.addTests(loader.loadTestsFromTestCase(VolumeAssertTest))
    suite.addTests(loader.loadTestsFromTestCase(RecordHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(WdaDeployTest))
    runner = unittest.TextTestRunner(verbosity=0)
    ut_result = runner.run(suite)

    for name, ok, detail in static_checks():
        print(f"[{'PASS' if ok else 'FAIL'}] {name}: {detail}")

    for name, ok, detail in api_checks():
        print(f"[{'PASS' if ok else 'FAIL'}] {name}: {detail}")

    failed = not ut_result.wasSuccessful()
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
