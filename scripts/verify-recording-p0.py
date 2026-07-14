#!/usr/bin/env python3
"""P0 录屏核心验收：widget 识别 / 识别率计算 / 启动耗时 / 性能指标阈值"""
import json
import os
import re
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))

STARTUP_MS_MAX = 3000
RECOGNITION_RATE_MIN = 95.0
PAINT_MS_MAX = 50
RECORD_FPS_MIN = 6
CPU_OK_RATE_MIN = 80.0

ORDER_RE = re.compile(r"(?i)(ORD|ORDER|SN|NO)[-_]?(\d{6,})")
PHONE_RE = re.compile(r"(?<!\d)(1[3-9]\d{9})(?!\d)")
ID_CARD_RE = re.compile(r"(?<!\d)(\d{17}[\dXx])(?!\d)")
EMAIL_RE = re.compile(r"([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})")


def desensitize_text(text: str) -> str:
    """与 DesensitizeUtil / desensitize.js 规则对齐。"""
    if not text:
        return text
    out = text
    out = PHONE_RE.sub(lambda m: m.group(1)[:3] + "****" + m.group(1)[-4:], out)
    out = ID_CARD_RE.sub(lambda m: m.group(1)[:4] + "**********" + m.group(1)[-4:], out)
    out = EMAIL_RE.sub(lambda m: (m.group(1)[:2] + "***@" + m.group(2)), out)

    def mask_order(m):
        s = m.group(0)
        chars = list(s)
        for i, c in enumerate(chars):
            if c.isdigit() and i > 4 and i < len(chars) - 3:
                chars[i] = "*"
        return "".join(chars)

    out = ORDER_RE.sub(mask_order, out)
    return out


def contains_sensitive(text: str) -> bool:
    if not text:
        return False
    return bool(PHONE_RE.search(text) or ID_CARD_RE.search(text) or EMAIL_RE.search(text) or ORDER_RE.search(text))


def compute_recognition_rate(steps: list) -> dict:
    """与 OperationRecordService.computeRecognitionMetrics 逻辑对齐。"""
    interactive_types = {
        "click", "tap_xy", "input", "select", "upload", "confirm_dialog", "tap_ocr", "long_press"
    }
    interactive = recognized = locator_hits = 0
    for step in steps:
        t = step.get("type", "")
        if t not in interactive_types:
            continue
        interactive += 1
        has_element = bool(step.get("element_name"))
        valid = step.get("locator_valid", has_element)
        ocr = t == "tap_ocr"
        if has_element or ocr or valid:
            recognized += 1
        if has_element and valid:
            locator_hits += 1
    rate = round(recognized * 10000.0 / interactive) / 100.0 if interactive else 0.0
    hit = round(locator_hits * 10000.0 / interactive) / 100.0 if interactive else 0.0
    return {"recognition_rate": rate, "locator_hit_rate": hit, "interactive": interactive}


def validate_session_meta(meta: dict) -> list[str]:
    errors = []
    if meta.get("startup_ms") is not None and meta["startup_ms"] > STARTUP_MS_MAX:
        errors.append(f"startup_ms={meta['startup_ms']} 超过 {STARTUP_MS_MAX}ms")
    if meta.get("recognition_rate") is not None and meta["recognition_rate"] < RECOGNITION_RATE_MIN:
        errors.append(f"recognition_rate={meta['recognition_rate']}% 低于 {RECOGNITION_RATE_MIN}%")
    if meta.get("paint_ms_avg") is not None and meta["paint_ms_avg"] > PAINT_MS_MAX:
        errors.append(f"paint_ms_avg={meta['paint_ms_avg']}ms 超过 {PAINT_MS_MAX}ms")
    if meta.get("record_fps_avg") is not None and meta["record_fps_avg"] < RECORD_FPS_MIN:
        errors.append(f"record_fps_avg={meta['record_fps_avg']} 低于 {RECORD_FPS_MIN}fps")
    grade = meta.get("performance_grade")
    if grade is not None and grade not in ("good", "fair", "heavy"):
        errors.append(f"performance_grade={grade} 非法")
    if meta.get("cpu_ok") is False and grade == "heavy":
        errors.append("cpu_ok=false 且 performance_grade=heavy")
    return errors


SAMPLE_GOOD = {
    "meta": {
        "startup_ms": 1200,
        "recognition_rate": 96.5,
        "paint_ms_avg": 22,
        "record_fps_avg": 10,
    },
    "steps": [
        {"type": "click", "element_name": "login_btn", "locator_valid": True, "video_offset_ms": 1000},
        {"type": "input", "element_name": "username", "locator_valid": True, "video_offset_ms": 2000},
        {"type": "select", "element_name": "city_spinner", "locator_valid": True, "video_offset_ms": 3000},
    ],
}


class RecognitionMetricsTest(unittest.TestCase):
    def test_compute_rate(self):
        m = compute_recognition_rate(SAMPLE_GOOD["steps"])
        self.assertEqual(m["interactive"], 3)
        self.assertGreaterEqual(m["recognition_rate"], 66.0)

    def test_validate_good_meta(self):
        meta = dict(SAMPLE_GOOD["meta"])
        self.assertEqual(validate_session_meta(meta), [])

    def test_validate_bad_startup(self):
        errs = validate_session_meta({"startup_ms": 5000})
        self.assertTrue(any("startup_ms" in e for e in errs))

    def test_validate_bad_recognition(self):
        errs = validate_session_meta({"recognition_rate": 80})
        self.assertTrue(any("recognition_rate" in e for e in errs))


class DesensitizeTest(unittest.TestCase):
    def test_phone(self):
        self.assertEqual(desensitize_text("13812345678"), "138****5678")

    def test_order(self):
        out = desensitize_text("ORDER1234567890")
        self.assertIn("*", out)
        self.assertFalse(contains_sensitive(out))

    def test_id_card(self):
        out = desensitize_text("110101199001011234")
        self.assertIn("**********", out)


class WidgetRecognitionTest(unittest.TestCase):
    @patch("record_helper._adb_display_size", return_value=(1080, 1920))
    @patch("record_helper.dump_ui_cached")
    @patch("record_helper.detect_context_from_xml")
    def test_upload_widget(self, mock_ctx, mock_dump, _mock_size):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node class="android.widget.ImageButton" content-desc="上传附件"
                resource-id="com.app:id/upload" bounds="[0,0][80,80]" clickable="true"/>
        </hierarchy>
        """
        r = inspect_point("dev1", 40, 40)
        self.assertIn(r["widget_type"], ("upload", "click", "hover"))

    @patch("record_helper.dump_ui_cached")
    def test_upload_ocr_fallback(self, mock_dump):
        from record_helper import _ocr_upload_fallback

        mock_dump.return_value = """
        <hierarchy>
          <node text="上传文件" bounds="[30,30][120,60]" clickable="true"/>
        </hierarchy>
        """
        r = _ocr_upload_fallback("dev1", 50, 45, {})
        self.assertEqual(r.get("widget_type"), "upload")
        self.assertEqual(r.get("suggested_step_type"), "upload")

    def test_validate_locators_on_screen(self):
        from record_helper import validate_locators_on_screen
        with patch("record_helper.dump_ui_cached") as mock_dump:
            mock_dump.return_value = """
            <hierarchy>
              <node resource-id="com.app:id/login_btn" text="登录" bounds="[0,0][100,50]"/>
            </hierarchy>
            """
            r = validate_locators_on_screen("dev1", {"id": "login_btn"})
            self.assertTrue(r.get("valid"))

    @patch("record_helper._adb_display_size", return_value=(1080, 1920))
    @patch("record_helper.dump_ui_cached")
    @patch("record_helper.detect_context_from_xml")
    def test_hover_widget_step_type(self, mock_ctx, mock_dump, _mock_size):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node class="android.view.View" text="菜单" bounds="[0,0][100,50]"
                clickable="true" long-clickable="true"/>
        </hierarchy>
        """
        r = inspect_point("dev1", 50, 25)
        self.assertEqual(r.get("widget_type"), "hover")
        self.assertEqual(r.get("suggested_step_type"), "hover")

    @patch("record_helper._adb_display_size", return_value=(1080, 1920))
    @patch("record_helper.dump_ui_cached")
    @patch("record_helper.detect_context_from_xml")
    def test_rich_text_widget(self, mock_ctx, mock_dump, _mock_size):
        from record_helper import inspect_point

        mock_ctx.return_value = "native"
        mock_dump.return_value = """
        <hierarchy>
          <node class="android.widget.EditText" text="" bounds="[0,0][200,80]"
                input-type="131073" clickable="true"/>
        </hierarchy>
        """
        r = inspect_point("dev1", 100, 40)
        self.assertEqual(r.get("widget_type"), "rich_text")
        self.assertEqual(r.get("suggested_step_type"), "rich_text")


def run_api_audit(base_url: str, token: str, session_id: int) -> int:
    import urllib.request

    req = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/v1/operation-records/{session_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode())
    data = body.get("data") or body
    steps_json = json.loads(data.get("steps_json") or "{}")
    meta = steps_json.get("meta") or {}
    metrics = compute_recognition_rate(steps_json.get("steps") or [])
    meta.update(metrics)
    errs = validate_session_meta(meta)
    print(f"  会话 #{session_id} recognition_rate={meta.get('recognition_rate')}% startup_ms={meta.get('startup_ms')} grade={meta.get('performance_grade')}")
    for e in errs:
        print(f"  FAIL: {e}")
    return 1 if errs else 0


def run_batch_audit(base_url: str, token: str, limit: int = 10) -> int:
    import urllib.request

    url = f"{base_url.rstrip('/')}/api/v1/operation-records/audit-summary?limit={limit}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        body = json.loads(resp.read().decode())
    summary = body.get("data") or body
    print(f"  批量审计 sample={summary.get('sample_count')} avg_rate={summary.get('avg_recognition_rate')}% cpu_ok_rate={summary.get('cpu_ok_rate')}%")
    code = 0
    if summary.get("avg_recognition_rate") is not None and summary["avg_recognition_rate"] < RECOGNITION_RATE_MIN:
        print(f"  FAIL: 平均识别率 {summary['avg_recognition_rate']}% 低于 {RECOGNITION_RATE_MIN}%")
        code = 1
    p0 = summary.get("p0_acceptance") or {}
    if p0 and p0.get("overall_passed") is False:
        print(f"  FAIL: P0 门禁未通过 {p0.get('failed_checks')}")
        code = 1
    if summary.get("cpu_ok_rate") is not None and summary["cpu_ok_rate"] < CPU_OK_RATE_MIN:
        print(f"  FAIL: CPU 达标率 {summary['cpu_ok_rate']}% 低于 {CPU_OK_RATE_MIN}%")
        code = 1
    for sess in summary.get("sessions") or []:
        meta = {
            "recognition_rate": sess.get("recognition_rate"),
            "startup_ms": sess.get("startup_ms"),
            "record_fps_avg": sess.get("record_fps_avg"),
            "paint_ms_avg": sess.get("paint_ms_avg"),
            "performance_grade": sess.get("performance_grade"),
            "cpu_ok": sess.get("cpu_ok"),
        }
        errs = validate_session_meta({k: v for k, v in meta.items() if v is not None})
        if errs:
            print(f"  会话 #{sess.get('id')} FAIL: {'; '.join(errs)}")
            code = 1
    return code


def main():
    print("  P0 录屏核心验收 (verify-recording-p0)")
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    suite.addTests(loader.loadTestsFromTestCase(RecognitionMetricsTest))
    suite.addTests(loader.loadTestsFromTestCase(DesensitizeTest))
    suite.addTests(loader.loadTestsFromTestCase(WidgetRecognitionTest))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    code = 0 if result.wasSuccessful() else 1

    api_base = os.environ.get("ATP_API_BASE", "")
    token = os.environ.get("ATP_TOKEN", "")
    session_id = os.environ.get("ATP_SESSION_ID", "")
    batch = os.environ.get("ATP_BATCH_AUDIT", "")
    if api_base and token and batch:
        print("\n  批量 API 审计 ...")
        limit = int(os.environ.get("ATP_BATCH_LIMIT", "10"))
        code |= run_batch_audit(api_base, token, limit)
    elif api_base and token and session_id:
        print("\n  API 会话审计 ...")
        code |= run_api_audit(api_base, token, int(session_id))

    sys.exit(code)


if __name__ == "__main__":
    main()
