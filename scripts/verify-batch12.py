#!/usr/bin/env python3
"""第十二批：SSO / 埋点校验 / 全局执行大屏 / 离线执行包 冒烟验证"""

from __future__ import annotations

import io
import json
import sys
import unittest
import zipfile
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))


class AnalyticsHelperTest(unittest.TestCase):
    @patch("analytics_helper._logcat_dump")
    def test_logcat_match(self, mock_dump):
        mock_dump.return_value = 'track event page_view {"page":"home"}'
        from analytics_helper import assert_analytics_event

        logs = assert_analytics_event("dev1", "page_view", {"page": "home"})
        self.assertTrue(any("logcat OK" in m for m in logs))

    @patch("analytics_helper.urllib.request.urlopen")
    def test_http_verify(self, mock_urlopen):
        payload = json.dumps({"events": [{"event_name": "click_buy", "props": {"sku": "1001"}}]})
        mock_urlopen.return_value.__enter__.return_value = MagicMock(
            read=MagicMock(return_value=payload.encode())
        )
        from analytics_helper import assert_analytics_event

        logs = assert_analytics_event("dev1", "click_buy", {"sku": "1001"}, verify_url="http://mock/analytics")
        self.assertTrue(any("http OK" in m for m in logs))


def _request(method: str, url: str, token: str | None = None, body: dict | None = None, timeout=15):
    import urllib.error
    import urllib.request

    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Type", "").startswith("application/octet"):
                return raw
            return json.loads(raw.decode())
    except urllib.error.HTTPError as e:
        err_body = e.read().decode()
        try:
            payload = json.loads(err_body)
        except json.JSONDecodeError:
            payload = {"message": err_body}
        raise RuntimeError(f"HTTP {e.code}: {payload}") from e


def login() -> str:
    data = _request("POST", "http://localhost:8080/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
    return data["data"]["token"]


def api_e2e() -> list[tuple[str, bool, str]]:
    results: list[tuple[str, bool, str]] = []
    try:
        sso_cfg = _request("GET", "http://localhost:8080/api/v1/auth/sso/config")
        enabled = sso_cfg.get("data", {}).get("enabled")
        results.append(("SSO 配置", enabled is True, json.dumps(sso_cfg.get("data", {}), ensure_ascii=False)[:100]))

        sso = _request("POST", "http://localhost:8080/api/v1/auth/sso/login", body={"sso_token": "testflow-sso-demo:admin"})
        token = sso.get("data", {}).get("token")
        results.append(("SSO 登录", bool(token), "admin JWT OK"))

        headers_token = token or login()
        results.append(("密码登录", bool(headers_token), "admin OK"))

        wall = _request("GET", "http://localhost:8080/api/v1/dashboard/wallboard", token=headers_token)
        wb = wall.get("data", {})
        wall_ok = "devices" in wb and "queue_depth" in wb and "running_tasks" in wb
        results.append(("执行大屏 API", wall_ok, f"queue={wb.get('queue_depth')}, running={wb.get('tasks', {}).get('running')}"))

        visual = {
            "version": 1,
            "steps": [{"type": "assert_analytics", "event_name": "page_view", "props_json": '{"page":"home"}', "enabled": True}],
        }
        preview = _request(
            "POST",
            "http://localhost:8080/api/v1/tasks/visual/preview",
            token=headers_token,
            body={"visual_json": json.dumps(visual)},
        )
        script = preview.get("data", {}).get("script", "")
        script_ok = "assert_analytics" in script and "page_view" in script
        results.append(("埋点脚本预览", script_ok, f"{len(script)} chars"))

        task = _request(
            "POST",
            "http://localhost:8080/api/v1/tasks",
            token=headers_token,
            body={
                "name": "Batch12-离线包验证",
                "platform": "android",
                "script_type": "visual",
                "script_content": json.dumps({"version": 1, "steps": [{"type": "wait", "seconds": 1, "enabled": True}]}),
                "app_package": "com.android.settings",
                "parallel_count": 1,
                "timeout_seconds": 120,
                "enable_recording": False,
            },
        )
        task_id = task.get("data", {}).get("id")
        results.append(("创建任务", bool(task_id), f"task_id={task_id}"))

        if task_id:
            pkg = _request("POST", f"http://localhost:8080/api/v1/tasks/{task_id}/offline-package", token=headers_token)
            filename = pkg.get("data", {}).get("filename")
            size = pkg.get("data", {}).get("size_bytes", 0)
            zip_ok = bool(filename) and size > 100
            results.append(("离线包生成", zip_ok, json.dumps(pkg.get("data", {}), ensure_ascii=False)))

            if filename:
                raw = _request("GET", f"http://localhost:8080/api/v1/offline-packages/{filename}/download", token=headers_token)
                zf = zipfile.ZipFile(io.BytesIO(raw))
                names = zf.namelist()
                dl_ok = "manifest.json" in names and "test_script.py" in names and "run_offline.py" in names
                results.append(("离线包下载", dl_ok, ",".join(names[:5])))
    except Exception as e:
        results.append(("API E2E", False, str(e)))
    return results


def static_checks() -> list[tuple[str, bool, str]]:
    checks: list[tuple[str, bool, str]] = []
    checks.append(("SSO Service", "SsoService" in (ROOT / "backend-java/src/main/java/com/atp/platform/service/SsoService.java").read_text(encoding="utf-8"), "SsoService.java"))
    checks.append(("离线包 Service", "OfflinePackageService" in (ROOT / "backend-java/src/main/java/com/atp/platform/service/OfflinePackageService.java").read_text(encoding="utf-8"), "OfflinePackageService.java"))
    gen = (ROOT / "backend-java/src/main/java/com/atp/platform/service/VisualScriptGenerator.java").read_text(encoding="utf-8")
    checks.append(("埋点步骤 assert_analytics", "assert_analytics" in gen, "VisualScriptGenerator"))
    checks.append(("大屏 wallboard", "wallboard" in (ROOT / "backend-java/src/main/java/com/atp/platform/service/ReportService.java").read_text(encoding="utf-8"), "ReportService"))
    checks.append(("执行器 analytics_helper", (EXECUTOR / "analytics_helper.py").exists(), "analytics_helper.py"))
    return checks


def main():
    print("=" * 60)
    print("  Batch12 验证：SSO / 埋点 / 大屏 / 离线包")
    print("=" * 60)

    suite = unittest.TestSuite()
    loader = unittest.TestLoader()
    suite.addTests(loader.loadTestsFromTestCase(AnalyticsHelperTest))
    unit_ok = unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful()

    print("\n--- 静态检查 ---")
    static_results = static_checks()
    static_ok = all(r[1] for r in static_results)
    for name, passed, detail in static_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")

    print("\n--- 后端 API（需 :8080 第十二批 JAR）---")
    api_results = api_e2e()
    for name, passed, detail in api_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")
    api_ok = all(r[1] for r in api_results) if api_results else False

    print("\n" + "=" * 60)
    if unit_ok and static_ok and api_ok:
        print("第十二批验证：全部通过")
        return
    if unit_ok and static_ok:
        print("单元+静态：通过；请确认 backend 已重启为第十二批 JAR")
        sys.exit(2)
    print("存在失败项")
    sys.exit(1)


if __name__ == "__main__":
    main()
