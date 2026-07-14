#!/usr/bin/env python3
"""M1 定位链闭环验收：evaluate / validate / resolve_locator_chain"""
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "executor"))

from record_helper import evaluate_locator, validate_locators_on_screen, _ordered_locator_keys  # noqa: E402
from locator_runtime import resolve_locator_chain  # noqa: E402

SAMPLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="" resource-id="" class="android.widget.FrameLayout" clickable="false" bounds="[0,0][1080,2400]">
    <node index="0" text="" resource-id="com.demo:id/login_btn" content-desc="密码登录" class="android.view.View" clickable="true" bounds="[100,200][980,280]" displayed="true"/>
    <node index="1" text="密码登录" resource-id="" class="android.view.View" clickable="false" bounds="[100,300][980,360]" displayed="true"/>
  </node>
</hierarchy>"""


class EvaluateLocatorTests(unittest.TestCase):
    def setUp(self):
        self.root = ET.fromstring(SAMPLE_XML)

    def test_id_clickable(self):
        ev = evaluate_locator(self.root, "id", "login_btn")
        self.assertTrue(ev["found"])
        self.assertTrue(ev["clickable"])

    def test_content_desc_clickable(self):
        ev = evaluate_locator(self.root, "content_desc", "密码登录")
        self.assertTrue(ev["clickable"])

    def test_text_not_clickable(self):
        ev = evaluate_locator(self.root, "text", "密码登录")
        self.assertTrue(ev["found"])
        self.assertFalse(ev["clickable"])
        self.assertEqual(ev["reason"], "not_clickable")

    def test_bounds(self):
        ev = evaluate_locator(self.root, "bounds", "[100,200][980,280]")
        self.assertTrue(ev["clickable"])

    def test_screen_ratio(self):
        ev = evaluate_locator(self.root, "screen_ratio", "0.5,0.2")
        self.assertTrue(ev["found"])


class OrderedChainTests(unittest.TestCase):
    def test_chain_order(self):
        locs = {"id": "a", "content_desc": "b", "bounds": "c"}
        chain = [{"type": "content_desc", "enabled": True}, {"type": "id", "enabled": True}]
        keys = _ordered_locator_keys(locs, chain)
        self.assertEqual(keys[0], "content_desc")
        self.assertEqual(keys[1], "id")


class ValidateChainTests(unittest.TestCase):
    def test_validate_prefers_clickable_in_chain(self):
        locs = {
            "text": "密码登录",
            "content_desc": "密码登录",
            "id": "login_btn",
        }
        chain = [
            {"type": "text", "enabled": True},
            {"type": "content_desc", "enabled": True},
            {"type": "id", "enabled": True},
        ]

        class FakeDump:
            def __init__(self, xml):
                self.xml = xml

        import record_helper as rh

        original = rh.dump_ui_cached
        rh.dump_ui_cached = lambda *a, **k: SAMPLE_XML
        rh.dump_ui = lambda *a, **k: SAMPLE_XML
        try:
            out = validate_locators_on_screen("emulator-5554", locs, chain)
            self.assertTrue(out["valid"])
            self.assertEqual(out["matched_by"], "content_desc")
            attempts = out["attempts"]
            self.assertEqual(attempts[0]["type"], "text")
            self.assertFalse(attempts[0]["clickable"])
        finally:
            rh.dump_ui_cached = original


class ResolveChainTests(unittest.TestCase):
    def test_resolve_fallback_to_id(self):
        import record_helper as rh

        original = rh.dump_ui_cached
        rh.dump_ui_cached = lambda *a, **k: SAMPLE_XML
        rh.dump_ui = lambda *a, **k: SAMPLE_XML
        try:
            locs = {"text": "密码登录", "id": "login_btn", "content_desc": "密码登录"}
            chain = [
                {"type": "text", "enabled": True},
                {"type": "id", "enabled": True},
            ]
            out = resolve_locator_chain("emulator-5554", locs, chain, display_name="登录")
            self.assertTrue(out["ok"])
            self.assertEqual(out["matched_by"], "id")
            self.assertIn("bounds", out)
        finally:
            rh.dump_ui_cached = original


if __name__ == "__main__":
    unittest.main(verbosity=2)
