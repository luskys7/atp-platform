#!/usr/bin/env python3
"""M3 控件池平台治理验收：静态契约 + 维度匹配逻辑"""
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class M3ContractTests(unittest.TestCase):
    def test_migration_exists(self):
        self.assertTrue((ROOT / "migrations" / "006_m3_control_pool_governance.sql").exists())

    def test_batch_validate_endpoint(self):
        src = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "controller" / "ControlController.java").read_text(encoding="utf-8")
        self.assertIn("/batch-validate", src)
        self.assertIn("/unstable-stats", src)
        self.assertIn("/archive", src)

    def test_four_dim_entity_fields(self):
        src = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "entity" / "ControlPool.java").read_text(encoding="utf-8")
        self.assertIn("teamId", src)
        self.assertIn("envTag", src)

    def test_task_context_resolve(self):
        src = (ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "service" / "ControlPoolService.java").read_text(encoding="utf-8")
        self.assertIn("contextFromTask", src)
        self.assertIn("scorePoolMatch", src)
        self.assertIn("collectElementNamesFromTask", src)
        self.assertIn("suite_refs", src)

    def test_governance_service(self):
        path = ROOT / "backend-java" / "src" / "main" / "java" / "com" / "atp" / "platform" / "service" / "ControlGovernanceService.java"
        self.assertTrue(path.exists())
        src = path.read_text(encoding="utf-8")
        self.assertIn("FAIL_STREAK_ARCHIVE_THRESHOLD", src)
        self.assertIn("unstableStats", src)

    def test_frontend_batch_page(self):
        self.assertTrue((ROOT / "frontend" / "src" / "views" / "ControlBatchValidate.vue").exists())
        router = (ROOT / "frontend" / "src" / "router" / "index.js").read_text(encoding="utf-8")
        self.assertIn("ControlBatchValidate", router)

    def test_picker_context_query(self):
        picker = (ROOT / "frontend" / "src" / "views" / "ElementPicker.vue").read_text(encoding="utf-8")
        review = (ROOT / "frontend" / "src" / "views" / "RecordCaseReview.vue").read_text(encoding="utf-8")
        self.assertIn("envTag", picker)
        self.assertIn("versionTag", review)


class DimensionMatchTests(unittest.TestCase):
    """复刻 Java normalizeDim / dimensionMatches 行为"""

    @staticmethod
    def normalize_dim(val):
        if val is None or str(val).strip() == "":
            return "*"
        return str(val).strip()

    @staticmethod
    def dimension_matches(pool_val, ctx_val):
        p = DimensionMatchTests.normalize_dim(pool_val)
        c = DimensionMatchTests.normalize_dim(ctx_val)
        if p == c:
            return True
        return p == "*" or c == "*"

    def test_empty_is_wildcard(self):
        self.assertTrue(self.dimension_matches("", "v1.0"))
        self.assertTrue(self.dimension_matches("v1.0", ""))

    def test_exact_match(self):
        self.assertTrue(self.dimension_matches("staging", "staging"))

    def test_mismatch(self):
        self.assertFalse(self.dimension_matches("prod", "staging"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
