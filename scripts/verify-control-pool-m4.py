#!/usr/bin/env python3
"""M4 扩展与规范验收：静态契约 + wait_rule / uiselector 逻辑"""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class M4ContractTests(unittest.TestCase):
    def test_migration_exists(self):
        self.assertTrue((ROOT / "migrations" / "007_m4_extension.sql").exists())

    def test_executor_ios_and_uiselector(self):
        self.assertTrue((ROOT / "executor" / "ios_dump_helper.py").exists())
        self.assertTrue((ROOT / "executor" / "uiselector_helper.py").exists())
        record = (ROOT / "executor" / "record_helper.py").read_text(encoding="utf-8")
        self.assertIn("uiselector", record)
        self.assertIn("wait_page_ready", record)
        self.assertIn("inspect_ios_point", record)
        runtime = (ROOT / "executor" / "locator_runtime.py").read_text(encoding="utf-8")
        self.assertIn("wait_rule", runtime)

    def test_webview_and_ocr_helpers(self):
        main_py = (ROOT / "executor" / "main.py").read_text(encoding="utf-8")
        self.assertIn("/switch-context", main_py)
        self.assertIn("/wait-page-ready", main_py)
        record = (ROOT / "executor" / "record_helper.py").read_text(encoding="utf-8")
        self.assertIn("_ocr_screen_fallback", record)

    def test_java_m4_fields_and_rbac(self):
        entity = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "entity" / "ControlPool.java").read_text(encoding="utf-8")
        self.assertIn("isCore", entity)
        self.assertIn("controlTag", entity)
        svc = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "service" / "ControlPoolService.java").read_text(encoding="utf-8")
        self.assertIn("validateElementName", svc)
        self.assertIn("assertCanEditPool", svc)
        self.assertIn("wait_rule", svc)

    def test_failure_report_api(self):
        ctrl = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "controller" / "ControlController.java").read_text(encoding="utf-8")
        self.assertIn("/locator-failure-stats", ctrl)
        self.assertTrue((ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "service" / "LocatorFailureReportService.java").exists())

    def test_controls_json_wait_rule(self):
        resolver = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "service" / "ControlResolverService.java").read_text(encoding="utf-8")
        self.assertIn("wait_rule", resolver)
        gen = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "service" / "VisualScriptGenerator.java").read_text(encoding="utf-8")
        self.assertIn("wait_rule_json", gen)
        self.assertIn("wait_page_ready", gen)

    def test_frontend_m4_pages(self):
        self.assertTrue((ROOT / "frontend" / "src" / "views" / "LocatorFailureReport.vue").exists())
        router = (ROOT / "frontend" / "src" / "router" / "index.js").read_text(encoding="utf-8")
        self.assertIn("LocatorFailureReport", router)
        picker = (ROOT / "frontend" / "src" / "views" / "ElementPicker.vue").read_text(encoding="utf-8")
        self.assertIn("switchWebViewContext", picker)
        self.assertIn("ocrFallbackPick", picker)
        self.assertIn("control_tag", picker)
        controls = (ROOT / "frontend" / "src" / "views" / "Controls.vue").read_text(encoding="utf-8")
        self.assertIn("validateElementName", controls)
        self.assertIn("isProtectedPool", controls)
        assist = (ROOT / "frontend" / "src" / "utils" / "locatorAssist.js").read_text(encoding="utf-8")
        self.assertIn("uiselector", assist)


class UiSelectorHelperTests(unittest.TestCase):
    def test_build_and_parse(self):
        sys.path.insert(0, str(ROOT / "executor"))
        from uiselector_helper import build_uiselector, parse_uiselector, find_nodes_for_uiselector
        import xml.etree.ElementTree as ET

        node = ET.Element("node", {
            "resource-id": "com.demo:id/login_btn",
            "text": "登录",
            "content-desc": "登录按钮",
            "class": "android.widget.Button",
            "clickable": "true",
            "bounds": "[0,0][100,100]",
        })
        expr = build_uiselector(node, "登录", "登录按钮", "com.demo:id/login_btn")
        self.assertIn("resourceId", expr)
        kv = parse_uiselector(expr)
        self.assertIn("resource_id", kv)
        self.assertEqual(kv["resource_id"], "login_btn")
        root = ET.Element("hierarchy")
        root.append(node)
        found = find_nodes_for_uiselector(root, expr)
        self.assertEqual(len(found), 1)


class ElementNameValidationTests(unittest.TestCase):
    @staticmethod
    def valid(name: str) -> bool:
        import re
        return bool(re.match(r"^[a-z][a-z0-9_]{2,47}$", name))

    def test_snake_case(self):
        self.assertTrue(self.valid("login_btn"))
        self.assertFalse(self.valid("LoginBtn"))
        self.assertFalse(self.valid("ab"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
