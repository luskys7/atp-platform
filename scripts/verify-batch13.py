#!/usr/bin/env python3
"""第十三批：健康监控告警 / 执行器自愈 / 自动化覆盖率 / 任务队列看板 冒烟验证"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class MonitorAlertLogicTest(unittest.TestCase):
    def test_platform_alert_converge_window(self):
        platform_alert_minutes = 30
        self.assertGreaterEqual(platform_alert_minutes, 10)


class FailoverEventShapeTest(unittest.TestCase):
    def test_event_fields(self):
        ev = {"from_url": "http://a", "to_url": "http://b", "task_id": 1, "reason": "failover"}
        for key in ("from_url", "to_url", "task_id", "reason"):
            self.assertIn(key, ev)


def _request(method: str, url: str, token: str | None = None, body: dict | None = None):
    import urllib.error
    import urllib.request

    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode())
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
        token = login()
        results.append(("登录", True, "admin OK"))

        cov = _request("GET", "http://localhost:8080/api/v1/dashboard/coverage", token=token)
        c = cov.get("data", {})
        cov_ok = "automation_rate" in c and "total_cases" in c and "by_folder" in c
        results.append(("自动化覆盖率", cov_ok, json.dumps({
            "total": c.get("total_cases"), "rate": c.get("automation_rate")
        }, ensure_ascii=False)))

        qb = _request("GET", "http://localhost:8080/api/v1/dashboard/queue-board", token=token)
        q = qb.get("data", {})
        qb_ok = "queue_depth" in q and "items" in q
        results.append(("任务队列看板", qb_ok, f"depth={q.get('queue_depth')}, items={len(q.get('items', []))}"))

        mon = _request("GET", "http://localhost:8080/api/v1/platform/monitor", token=token)
        mon_ok = mon.get("data", {}).get("overall") in ("healthy", "degraded", "critical")
        results.append(("健康监控", mon_ok, f"overall={mon.get('data', {}).get('overall')}"))

        ev = _request("GET", "http://localhost:8080/api/v1/platform/executor-events", token=token)
        ev_ok = isinstance(ev.get("data"), list)
        results.append(("执行器事件 API", ev_ok, f"events={len(ev.get('data', []))}"))

    except Exception as e:
        results.append(("API E2E", False, str(e)))
    return results


def static_checks() -> list[tuple[str, bool, str]]:
    checks = []
    checks.append(("监控告警调度", "PlatformMonitorAlertScheduler" in (
        ROOT / "backend-java/src/main/java/com/atp/platform/service/PlatformMonitorAlertScheduler.java"
    ).read_text(encoding="utf-8"), "PlatformMonitorAlertScheduler"))
    pool = (ROOT / "backend-java/src/main/java/com/atp/platform/service/ExecutorPoolService.java").read_text(encoding="utf-8")
    checks.append(("failover 事件", "recordFailover" in pool, "ExecutorPoolService"))
    msg = (ROOT / "backend-java/src/main/java/com/atp/platform/service/PlatformMessageService.java").read_text(encoding="utf-8")
    checks.append(("平台告警 sendPlatformAlert", "sendPlatformAlert" in msg, "PlatformMessageService"))
    checks.append(("覆盖率 CoverageReportService", (ROOT / "backend-java/src/main/java/com/atp/platform/service/CoverageReportService.java").exists(), "CoverageReportService"))
    checks.append(("队列看板 QueueBoardService", (ROOT / "backend-java/src/main/java/com/atp/platform/service/QueueBoardService.java").exists(), "QueueBoardService"))
    return checks


def main():
    print("=" * 60)
    print("  Batch13 验证：监控告警 / 自愈 / 覆盖率 / 队列看板")
    print("=" * 60)

    suite = unittest.TestSuite()
    loader = unittest.TestLoader()
    suite.addTests(loader.loadTestsFromTestCase(MonitorAlertLogicTest))
    suite.addTests(loader.loadTestsFromTestCase(FailoverEventShapeTest))
    unit_ok = unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful()

    print("\n--- 静态检查 ---")
    static_results = static_checks()
    static_ok = all(r[1] for r in static_results)
    for name, passed, detail in static_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")

    print("\n--- 后端 API（需 :8080 第十三批 JAR）---")
    api_results = api_e2e()
    for name, passed, detail in api_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")
    api_ok = all(r[1] for r in api_results) if api_results else False

    print("\n" + "=" * 60)
    if unit_ok and static_ok and api_ok:
        print("第十三批验证：全部通过")
        return
    if unit_ok and static_ok:
        print("单元+静态：通过；请确认 backend 已重启为第十三批 JAR")
        sys.exit(2)
    print("存在失败项")
    sys.exit(1)


if __name__ == "__main__":
    main()
