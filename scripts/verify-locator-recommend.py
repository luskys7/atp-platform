#!/usr/bin/env python3
"""上下文自动识别 + 平台化定位推荐验收"""
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "executor"))

from locator_recommend import build_recommended_chain, pick_primary_from_locators, score_locator
from webview_helper import detect_context_at_point, should_auto_switch_context, node_in_webview_context


class RecommendEngineTests(unittest.TestCase):
    def test_android_prefers_id_over_text(self):
        locs = {
            "id": "login_btn",
            "resource_id": "com.demo:id/login_btn",
            "uiselector": 'new UiSelector().resourceId("login_btn")',
            "text": "登录",
            "class_name": "Button",
        }
        chain = build_recommended_chain(locs, "android")
        rec = next(i for i in chain if i.get("recommended"))
        self.assertIn(rec["type"], ("id", "resource_id", "uiselector"))
        self.assertGreater(score_locator("id", "login_btn", "android"), score_locator("text", "登录", "android"))

    def test_android_skips_dynamic_text_as_recommended(self):
        locs = {"text": "验证码 839201", "class_name": "TextView"}
        chain = build_recommended_chain(locs, "android")
        rec = next(i for i in chain if i.get("recommended"))
        self.assertNotEqual(rec["type"], "text")

    def test_ios_prefers_accessibility_id(self):
        locs = {
            "accessibility_id": "login_button",
            "id": "login_button",
            "nspredicate": 'name == "login_button"',
            "text": "Sign In",
        }
        chain = build_recommended_chain(locs, "ios")
        rec = next(i for i in chain if i.get("recommended"))
        self.assertEqual(rec["type"], "accessibility_id")
        lt, lv = pick_primary_from_locators(locs, "ios")
        self.assertEqual(lt, "accessibility")
        self.assertEqual(lv, "login_button")


class WebViewAutoContextTests(unittest.TestCase):
    def _sample_hierarchy(self):
        return ET.fromstring("""
        <hierarchy>
          <node class="android.widget.FrameLayout" bounds="[0,0][1080,1920]">
            <node class="android.webkit.WebView" bounds="[0,200][1080,1800]" clickable="true">
              <node class="android.view.View" bounds="[100,400][980,500]" text="提交" clickable="true"/>
            </node>
          </node>
        </hierarchy>
        """)

    def test_detect_point_inside_webview(self):
        root = self._sample_hierarchy()
        self.assertEqual(detect_context_at_point(root, 500, 450), "webview")

    def test_should_auto_switch_on_hybrid_page(self):
        root = self._sample_hierarchy()
        self.assertTrue(should_auto_switch_context("hybrid", "webview", {"class": "android.view.View"}))
        inner = root.find(".//node[@text='提交']")
        self.assertTrue(node_in_webview_context(root, inner))


class ContractTests(unittest.TestCase):
    def test_files_exist(self):
        self.assertTrue((ROOT / "executor" / "locator_recommend.py").exists())
        src = (ROOT / "executor" / "record_helper.py").read_text(encoding="utf-8")
        self.assertIn("auto_switch_for_inspect", src)
        self.assertIn("build_recommended_chain", src)
        picker = (ROOT / "frontend" / "src" / "views" / "ElementPicker.vue").read_text(encoding="utf-8")
        self.assertIn("auto_context_switched", picker)
        self.assertIn("recommendReasonText", picker)
        assist = (ROOT / "frontend" / "src" / "utils" / "locatorAssist.js").read_text(encoding="utf-8")
        self.assertIn("pickRecommendedIndex", assist)


if __name__ == "__main__":
    unittest.main(verbosity=2)
