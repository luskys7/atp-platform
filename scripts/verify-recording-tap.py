#!/usr/bin/env python3
"""录制点击响应验收：tap 优先窗口、dump 避让、inspect 不阻塞 tap 线程池。"""
import sys
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent.parent
EXECUTOR = ROOT / "executor"
sys.path.insert(0, str(EXECUTOR))


class TapPriorityTest(unittest.TestCase):
    def setUp(self):
        import adb_client as ac

        self.ac = ac
        ac._tap_priority_until.clear()
        ac._dump_inflight.clear()
        ac._last_dump_finished_at.clear()
        ac._last_interaction_at.clear()

    def test_tap_priority_blocks_dump_slot(self):
        self.ac.request_tap_priority("dev1", 2.0)
        self.assertTrue(self.ac.tap_priority_active("dev1"))
        self.assertFalse(self.ac._dump_slot_available("dev1"))

    def test_dump_aborts_when_tap_priority_mid_flight(self):
        calls = {"n": 0}

        def fake_run(args, timeout=10):
            calls["n"] += 1
            proc = MagicMock()
            proc.returncode = 0
            return proc

        with patch.object(Path, "exists", return_value=True):
            with patch.object(self.ac, "adb_run", side_effect=fake_run):
                ok = self.ac._dump_ui_locked("dev1", "/tmp/x.xml", "/sdcard/x.xml", 3)
        self.assertTrue(ok)
        self.assertEqual(calls["n"], 2)

        calls["n"] = 0
        with patch.object(self.ac, "adb_run", side_effect=fake_run):
            self.ac.request_tap_priority("dev1", 2.0)
            ok = self.ac._dump_ui_locked("dev1", "/tmp/x.xml", "/sdcard/x.xml", 3)
        self.assertFalse(ok)
        self.assertEqual(calls["n"], 0)

    def test_adb_shell_tap_retries_under_lock_contention(self):
        lock = threading.Lock()
        lock.acquire()
        results = []

        def fake_run(args, timeout=10):
            proc = MagicMock()
            proc.returncode = 0
            results.append(list(args))
            return proc

        def release_later():
            time.sleep(0.15)
            lock.release()

        with patch.object(self.ac, "_adb_lock", lock):
            with patch.object(self.ac, "_run_subprocess", side_effect=fake_run):
                t = threading.Thread(target=release_later, daemon=True)
                t.start()
                proc = self.ac.adb_shell("dev1", "input", "tap", "100", "200", timeout=2)
                t.join(timeout=1)
        self.assertEqual(proc.returncode, 0)
        self.assertTrue(any("input" in r and "tap" in r for r in results))


class InspectNonBlockingTest(unittest.TestCase):
    @patch("record_helper.interaction_guard_blocks_dump", return_value=True)
    @patch("record_helper.tap_priority_active", return_value=False)
    def test_schedule_refresh_skips_during_interaction(self, _tap, _guard):
        import record_helper as rh

        rh._refresh_inflight.clear()
        rh._last_refresh_at.clear()
        with patch.object(rh.threading, "Thread") as mock_thread:
            rh._schedule_ui_refresh("dev1", force=True)
            mock_thread.assert_not_called()

    @patch("record_helper.dump_ui_cached", return_value="")
    def test_inspect_cache_miss_no_blocking_dump(self, mock_cached):
        from record_helper import inspect_point

        with patch("record_helper._schedule_ui_refresh") as mock_sched:
            r = inspect_point("dev1", 50, 50, blocking=False)
            self.assertEqual(r.get("inspect_error"), "cache_miss")
            mock_sched.assert_called_once()


def main():
    print("  录制点击响应验收 (verify-recording-tap)")
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    suite.addTests(loader.loadTestsFromTestCase(TapPriorityTest))
    suite.addTests(loader.loadTestsFromTestCase(InspectNonBlockingTest))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)


if __name__ == "__main__":
    main()
