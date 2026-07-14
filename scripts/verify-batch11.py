#!/usr/bin/env python3
"""第十一批：干扰降噪 / 后台保活 / 控件模糊匹配 / 用例评审 / 批量脚本修复 冒烟验证"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))


class InterferenceHelperTest(unittest.TestCase):
    @patch("interference_helper.subprocess.run")
    def test_enable_focus_mode(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        from interference_helper import enable_focus_mode, disable_focus_mode

        logs = enable_focus_mode("dev1")
        self.assertTrue(any("zen_mode" in m for m in logs))
        self.assertTrue(mock_run.called)
        restore = disable_focus_mode("dev1")
        self.assertTrue(any("restored" in m for m in restore))


class KeepaliveHelperTest(unittest.TestCase):
    @patch("keepalive_helper.subprocess.run")
    def test_enable_keep_awake(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        from keepalive_helper import enable_keep_awake, disable_keep_awake

        logs = enable_keep_awake("dev1")
        self.assertTrue(any("stay on" in m.lower() for m in logs))
        restore = disable_keep_awake("dev1")
        self.assertTrue(any("restored" in m for m in restore))


class FuzzySimilarityLogicTest(unittest.TestCase):
    def test_levenshtein_threshold(self):
        """模拟 ControlPoolService：编辑距离相似度 >= 0.72"""

        def similarity(a: str, b: str) -> float:
            x, y = a.lower(), b.lower()
            if x == y:
                return 1.0
            if x in y or y in x:
                return 0.85
            dp = [[0] * (len(y) + 1) for _ in range(len(x) + 1)]
            for i in range(len(x) + 1):
                dp[i][0] = i
            for j in range(len(y) + 1):
                dp[0][j] = j
            for i in range(1, len(x) + 1):
                for j in range(1, len(y) + 1):
                    cost = 0 if x[i - 1] == y[j - 1] else 1
                    dp[i][j] = min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            max_len = max(len(x), len(y))
            return 1.0 - dp[len(x)][len(y)] / max_len if max_len else 0

        self.assertGreaterEqual(similarity("btn_login_test", "btn_login_tst"), 0.72)
        self.assertLess(similarity("btn_login", "btn_logout"), 0.72)


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
    import time

    suffix = str(int(time.time() * 1000) % 100000)
    results: list[tuple[str, bool, str]] = []
    try:
        token = login()
        results.append(("登录", True, "admin OK"))
        base = "http://localhost:8080/api/v1"
        pkg = "com.batch11.verify"
        exact_name = f"b11_ctrl_{suffix}"
        fuzzy_query = exact_name[:-1] + ("z" if exact_name[-1] != "z" else "y")

        pool_body = {
            "app_package": pkg,
            "page_name": "Login",
            "element_name": exact_name,
            "platform": "android",
            "locator_type": "id",
            "locator_value": "com.demo:id/login",
        }
        pool = _request("POST", f"{base}/controls/pool", token, pool_body)
        pool_id = pool.get("data", {}).get("id")
        results.append(("创建控件池", bool(pool_id), f"pool_id={pool_id}"))

        resolve = _request(
            "POST",
            f"{base}/controls/resolve",
            token,
            {"app_package": pkg, "element_name": fuzzy_query},
        )
        source = resolve.get("data", {}).get("source", "")
        element_name = resolve.get("data", {}).get("element_name") or resolve.get("data", {}).get("elementName", "")
        fuzzy_ok = source == "pool_fuzzy" and element_name == exact_name
        results.append(("模糊匹配 resolve", fuzzy_ok, f"source={source}, element={element_name}"))

        case_body = {
            "name": "Batch11-评审流验证",
            "platform": "android",
            "script_type": "visual",
            "script_content": json.dumps({"version": 1, "steps": []}),
            "app_package": pkg,
        }
        case = _request("POST", f"{base}/cases", token, case_body)
        case_id = case.get("data", {}).get("id")
        status = case.get("data", {}).get("case_status", "")
        results.append(("创建草稿用例", bool(case_id) and status == "draft", f"case_id={case_id}, status={status}"))

        if case_id:
            submitted = _request("POST", f"{base}/cases/{case_id}/submit-review", token)
            st1 = submitted.get("data", {}).get("case_status", "")
            approved = _request("POST", f"{base}/cases/{case_id}/approve", token)
            st2 = approved.get("data", {}).get("case_status", "")
            review_ok = st1 == "review" and st2 == "active"
            results.append(("评审流 draft→review→active", review_ok, f"{status}→{st1}→{st2}"))

            case2 = _request("POST", f"{base}/cases", token, {**case_body, "name": "Batch11-驳回验证"})
            cid2 = case2.get("data", {}).get("id")
            if cid2:
                _request("POST", f"{base}/cases/{cid2}/submit-review", token)
                rejected = _request("POST", f"{base}/cases/{cid2}/reject", token, {"reason": "测试驳回"})
                st3 = rejected.get("data", {}).get("case_status", "")
                results.append(("评审驳回", st3 == "draft", f"status={st3}"))

        old_name = f"b11_old_{suffix}"
        new_name = f"b11_new_{suffix}"
        _request("POST", f"{base}/controls/pool", token, {
            **pool_body,
            "element_name": old_name,
            "locator_value": "com.demo:id/old",
        })
        replace = _request(
            "POST",
            f"{base}/controls/pool/batch-replace",
            token,
            {"app_package": pkg, "old_name": old_name, "new_name": new_name, "update_cases": False},
        )
        rep = replace.get("data", {})
        batch_ok = rep.get("pool_updated", 0) >= 1
        results.append(("批量脚本修复", batch_ok, json.dumps(rep, ensure_ascii=False)))

    except Exception as e:
        results.append(("API E2E", False, str(e)))
    return results


def static_checks() -> list[tuple[str, bool, str]]:
    checks: list[tuple[str, bool, str]] = []
    main_py = (EXECUTOR / "main.py").read_text(encoding="utf-8")
    checks.append(("执行器干扰降噪", "enable_focus_mode" in main_py and "干扰降噪" in main_py, "main.py"))
    checks.append(("执行器后台保活", "enable_keep_awake" in main_py and "保活" in main_py, "main.py"))

    pool_java = (ROOT / "backend-java/src/main/java/com/atp/platform/service/ControlPoolService.java").read_text(encoding="utf-8")
    checks.append(("模糊匹配 fuzzyLookup", "fuzzyLookup" in pool_java and "pool_fuzzy" in pool_java, "ControlPoolService"))
    checks.append(("批量替换 batchReplace", "batchReplaceElementName" in pool_java, "ControlPoolService"))

    case_java = (ROOT / "backend-java/src/main/java/com/atp/platform/service/CaseCatalogService.java").read_text(encoding="utf-8")
    checks.append(("用例评审 submitReview", "submitReview" in case_java and "approveCase" in case_java, "CaseCatalogService"))
    return checks


def main():
    print("=" * 60)
    print("  Batch11 验证：干扰降噪 / 保活 / 模糊匹配 / 评审 / 批量修复")
    print("=" * 60)

    suite = unittest.TestSuite()
    loader = unittest.TestLoader()
    suite.addTests(loader.loadTestsFromTestCase(InterferenceHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(KeepaliveHelperTest))
    suite.addTests(loader.loadTestsFromTestCase(FuzzySimilarityLogicTest))
    unit_ok = unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful()

    print("\n--- 静态检查 ---")
    static_results = static_checks()
    static_ok = True
    for name, passed, detail in static_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")
        static_ok = static_ok and passed

    print("\n--- 后端 API（需 :8080 第十一批 JAR）---")
    api_results = api_e2e()
    for name, passed, detail in api_results:
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: {detail}")
    api_ok = all(r[1] for r in api_results) if api_results else False

    print("\n" + "=" * 60)
    if unit_ok and static_ok and api_ok:
        print("第十一批验证：全部通过")
        return
    if unit_ok and static_ok:
        print("单元+静态：通过；请确认 backend 已重启为第十一批 JAR")
        sys.exit(2)
    print("存在失败项")
    sys.exit(1)


if __name__ == "__main__":
    main()
