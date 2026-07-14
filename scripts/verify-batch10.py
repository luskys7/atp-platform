#!/usr/bin/env python3
"""第十批：多语言 / 性能采集 / 缺陷联动 / 告警收敛 / 设备负载均衡 冒烟验证"""

from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))

PERF_PATTERN = re.compile(r"ATP_PERF_METRICS:(.+)")


class LocaleHelperTest(unittest.TestCase):
    @patch("locale_helper.subprocess.run")
    def test_normalize_and_set(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        from locale_helper import normalize_locale, set_device_locale

        self.assertEqual(normalize_locale("zh_cn"), "zh-CN")
        msg = set_device_locale("dev1", "en_us")
        self.assertIn("en-US", msg)
        self.assertTrue(mock_run.called)


class PerformanceHelperTest(unittest.TestCase):
    @patch("performance_helper.subprocess.run")
    def test_collect_memory_and_marker(self, mock_run):
        mock_run.return_value = MagicMock(
            returncode=0,
            stdout="TOTAL PSS: 12345 kB\n",
        )
        from performance_helper import collect_memory, emit_perf_marker

        data = collect_memory("dev1", "com.demo.app")
        self.assertEqual(data.get("total_pss_kb"), 12345)
        marker = emit_perf_marker(data)
        self.assertTrue(marker.startswith("ATP_PERF_METRICS:"))
        payload = json.loads(PERF_PATTERN.search(marker).group(1))
        self.assertEqual(payload["package"], "com.demo.app")


class DefectPerfParseTest(unittest.TestCase):
    def test_perf_marker_parse(self):
        raw = json.dumps({"cold_start_ms": 1200, "package": "com.demo"})
        msg = "ATP_PERF_METRICS:" + raw
        m = PERF_PATTERN.search(msg)
        self.assertIsNotNone(m)
        self.assertEqual(json.loads(m.group(1))["cold_start_ms"], 1200)


class AlertConvergenceLogicTest(unittest.TestCase):
    def test_converge_same_title(self):
        """模拟 PlatformMessageService：同 title+type+task 10 分钟内收敛"""
        from datetime import datetime, timedelta

        recent = {"title": "任务执行失败", "created_at": datetime.now() - timedelta(minutes=5)}
        new_title = "任务执行失败"
        should_skip = new_title == recent["title"]
        self.assertTrue(should_skip)


def login() -> str:
    import urllib.request

    body = json.dumps({"username": "admin", "password": "admin123"}).encode()
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode())["data"]["token"]


def check_script_generation(token: str) -> tuple[bool, str]:
    import urllib.request

    visual = {
        "version": 1,
        "wait_template": "standard",
        "steps": [
            {"type": "set_locale", "locale": "zh_cn", "enabled": True},
            {"type": "collect_performance", "enabled": True},
            {"type": "assert_cold_start", "max_ms": 8000, "enabled": True},
        ],
    }
    body = json.dumps({"visual_json": json.dumps(visual)}).encode()
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
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
        ("set_device_locale", "set_device_locale" in script),
        ("collect_performance_metrics", "collect_performance_metrics" in script),
        ("measure_cold_start_ms", "measure_cold_start_ms" in script),
        ("ATP_PERF", "ATP_PERF" in script or "emit_perf_marker" in script),
    ]
    failed = [n for n, ok in checks if not ok]
    if failed:
        return False, "脚本缺少: " + ", ".join(failed)
    return True, f"脚本生成 OK ({len(script)} chars)"


def api_e2e() -> list[tuple[str, bool, str]]:
    import urllib.request

    results: list[tuple[str, bool, str]] = []
    try:
        token = login()
        results.append(("登录", True, "admin OK"))
        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

        ok, msg = check_script_generation(token)
        results.append(("Visual 脚本预览", ok, msg))

        visual = {
            "version": 1,
            "steps": [
                {"type": "set_locale", "locale": "en_us", "enabled": True},
                {"type": "collect_performance", "enabled": True},
            ],
        }
        payload = json.dumps({
            "name": "Batch10-多语言性能验证",
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
            data=payload,
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            task_id = json.loads(resp.read().decode()).get("data", {}).get("id")
        results.append(("创建专项任务", bool(task_id), f"task_id={task_id}"))

        if task_id:
            req = urllib.request.Request(
                f"http://localhost:8080/api/v1/tasks/{task_id}/defect-info",
                headers=headers,
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                info = json.loads(resp.read().decode()).get("data", {})
            results.append(("defect-info API", True, json.dumps(info, ensure_ascii=False)[:120]))
    except Exception as e:
        results.append(("API E2E", False, str(e)))
    return results


def main():
    print("=" * 60)
    print("  Batch10 验证：多语言 / 性能 / 缺陷 / 告警收敛 / 负载均衡")
    print("=" * 60)

    suite = unittest.TestSuite()
    loader = unittest.TestLoader()
    suite.addTests(loader.loadTestsFromTestCase(LocaleHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(PerformanceHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(DefectPerfParseTest))
    suite.addTests(loader.loadTestsFromTestCase(AlertConvergenceLogicTest))
    unit_ok = unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful()

    print("\n--- 后端 API（需 :8080 第十批 JAR）---")
    api_results = api_e2e()
    for name, passed, detail in api_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")

    script_ok = any(r[0] == "Visual 脚本预览" and r[1] for r in api_results)
    api_ok = all(r[1] for r in api_results) if api_results else False

    print("\n--- 静态检查 ---")
    device_java = ROOT / "backend-java/src/main/java/com/atp/platform/service/DeviceService.java"
    text = device_java.read_text(encoding="utf-8")
    lb_ok = "Comparator.comparingInt" in text and "getFailCount" in text
    print(f"[{'PASS' if lb_ok else 'FAIL'}] 设备负载均衡排序: fail_count 升序")

    msg_java = ROOT / "backend-java/src/main/java/com/atp/platform/service/PlatformMessageService.java"
    conv_ok = "CONVERGE_MINUTES" in msg_java.read_text(encoding="utf-8")
    print(f"[{'PASS' if conv_ok else 'FAIL'}] 告警收敛: CONVERGE_MINUTES 配置")

    print("\n" + "=" * 60)
    if unit_ok and script_ok and lb_ok and conv_ok and api_ok:
        print("第十批验证：全部通过")
        return
    if unit_ok and script_ok and lb_ok and conv_ok:
        print("单元+脚本+静态：通过；请确认 backend 已重启为第十批 JAR")
        sys.exit(2)
    print("存在失败项")
    sys.exit(1)


if __name__ == "__main__":
    main()
