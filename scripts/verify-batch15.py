#!/usr/bin/env python3
"""Batch15：widget_type 推断 / step_marker_helper 冒烟验证"""
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))


class WidgetTypeTest(unittest.TestCase):
    @patch("record_helper.dump_ui")
    @patch("record_helper.detect_context")
    def test_spinner_select(self, mock_ctx, mock_dump):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node class="android.widget.Spinner" text="请选择" bounds="[0,0][100,50]" clickable="true"/>
        </hierarchy>
        """
        r = inspect_point("dev1", 50, 25)
        self.assertEqual(r["widget_type"], "select")
        self.assertEqual(r["suggested_step_type"], "select")

    @patch("record_helper.dump_ui")
    @patch("record_helper.detect_context")
    def test_dialog_confirm(self, mock_ctx, mock_dump):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node class="android.app.AlertDialog" bounds="[0,0][500,800]">
            <node class="android.widget.Button" text="确定" bounds="[100,700][200,760]" clickable="true"/>
          </node>
        </hierarchy>
        """
        r = inspect_point("dev1", 150, 730)
        self.assertEqual(r["widget_type"], "confirm_dialog")

    @patch("record_helper.dump_ui")
    @patch("record_helper.detect_context")
    def test_absolute_xpath(self, mock_ctx, mock_dump):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node class="android.widget.Button" text="登录" resource-id="com.app:id/login"
                bounds="[100,200][300,260]" clickable="true"/>
        </hierarchy>
        """
        r = inspect_point("dev1", 150, 230)
        locs = r.get("locators") or {}
        self.assertIn("absolute_xpath", locs)
        self.assertTrue(str(locs["absolute_xpath"]).startswith("/hierarchy/"))


class StepMarkerTest(unittest.TestCase):
    def test_emit_format(self):
        from step_marker_helper import emit_step_begin, emit_step_end, recording_offset_ms
        import io
        from contextlib import redirect_stdout

        os.environ["ATP_RECORDING_STARTED_AT"] = str(__import__("time").time() - 2)
        buf = io.StringIO()
        with redirect_stdout(buf):
            emit_step_begin(1, "click", "登录按钮")
            emit_step_end(1, "ok")
        out = buf.getvalue()
        self.assertIn("ATP_STEP_BEGIN step=1 type=click", out)
        self.assertIn("offset_ms=", out)
        self.assertIn("ATP_STEP_END step=1 status=ok", out)
        self.assertGreater(recording_offset_ms(), 1000)


def main():
    print("  Batch15 验证：widget_type / step_marker")
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    suite.addTests(loader.loadTestsFromTestCase(WidgetTypeTest))
    suite.addTests(loader.loadTestsFromTestCase(StepMarkerTest))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)


if __name__ == "__main__":
    main()
