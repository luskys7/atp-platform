#!/usr/bin/env python3
"""M2 相对定位 + 可见性过滤验收"""
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "executor"))

from record_helper import evaluate_locator, validate_locators_on_screen  # noqa: E402
from relative_locator import (  # noqa: E402
    build_parent_index,
    build_anchor_adjacent,
    build_region_locator,
    find_nodes_for_relative_locator,
    node_is_effectively_visible,
)
from locator_runtime import resolve_locator_chain  # noqa: E402

LIST_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]" displayed="true">
    <node index="0" text="" resource-id="com.demo:id/recycler" class="androidx.recyclerview.widget.RecyclerView" bounds="[0,400][1080,2000]" displayed="true">
      <node index="0" text="" resource-id="com.demo:id/item_row" class="android.widget.LinearLayout" clickable="true" bounds="[0,400][1080,520]" displayed="true"/>
      <node index="1" text="" resource-id="com.demo:id/item_row" class="android.widget.LinearLayout" clickable="true" bounds="[0,520][1080,640]" displayed="true"/>
      <node index="2" text="" resource-id="com.demo:id/item_row" class="android.widget.LinearLayout" clickable="true" bounds="[0,640][1080,760]" displayed="true"/>
    </node>
    <node index="1" text="用户名" class="android.widget.TextView" clickable="false" bounds="[80,200][300,260]" displayed="true"/>
    <node index="2" text="" resource-id="com.demo:id/edit_name" class="android.widget.EditText" clickable="true" bounds="[320,190][980,270]" displayed="true"/>
    <node index="3" text="" class="android.widget.Button" clickable="true" bounds="[100,900][980,980]" displayed="false" enabled="false"/>
    <node index="4" text="" resource-id="com.demo:id/dialog_panel" class="android.app.Dialog" bounds="[100,600][980,1400]" displayed="true">
      <node index="0" text="确认" content-desc="确认" class="android.widget.Button" clickable="true" bounds="[540,1280][900,1360]" displayed="true"/>
    </node>
  </node>
</hierarchy>"""


class RelativeBuildTests(unittest.TestCase):
    def setUp(self):
        self.root = ET.fromstring(LIST_XML)
        self.item2 = None
        for node in self.root.iter("node"):
            if node.get("resource-id") == "com.demo:id/item_row" and node.get("bounds") == "[0,640][1080,760]":
                self.item2 = node
                break
        self.assertIsNotNone(self.item2)

    def test_build_parent_index(self):
        val = build_parent_index(self.item2, self.root)
        self.assertIn("container=recycler", val)
        self.assertIn("index=2", val)

    def test_build_anchor_adjacent(self):
        edit = None
        for node in self.root.iter("node"):
            if node.get("resource-id") == "com.demo:id/edit_name":
                edit = node
                break
        val = build_anchor_adjacent(edit, self.root)
        self.assertIn("anchor=用户名", val)
        self.assertIn("dir=right", val)

    def test_build_region_locator(self):
        confirm = None
        for node in self.root.iter("node"):
            if (node.get("text") or "") == "确认":
                confirm = node
                break
        val = build_region_locator(confirm, self.root, "确认", "确认", "")
        self.assertIn("region=", val)
        self.assertIn("type=content_desc", val)
        self.assertIn("value=确认", val)


class RelativeResolveTests(unittest.TestCase):
    def setUp(self):
        self.root = ET.fromstring(LIST_XML)

    def test_parent_index_resolve(self):
        nodes = find_nodes_for_relative_locator(self.root, "parent_index", "container=recycler|index=1")
        self.assertEqual(len(nodes), 1)
        self.assertEqual(nodes[0].get("bounds"), "[0,520][1080,640]")

    def test_anchor_adjacent_resolve(self):
        nodes = find_nodes_for_relative_locator(self.root, "anchor_adjacent", "anchor=用户名|dir=right")
        self.assertEqual(len(nodes), 1)
        self.assertEqual(nodes[0].get("resource-id"), "com.demo:id/edit_name")

    def test_region_locator_resolve(self):
        nodes = find_nodes_for_relative_locator(
            self.root, "region_locator", "region=[100,600][980,1400]|type=content_desc|value=确认"
        )
        self.assertEqual(len(nodes), 1)
        self.assertEqual((nodes[0].get("text") or ""), "确认")

    def test_hidden_node_filtered(self):
        hidden = None
        for node in self.root.iter("node"):
            if node.get("displayed") == "false":
                hidden = node
                break
        self.assertFalse(node_is_effectively_visible(hidden, self.root))
        ev = evaluate_locator(self.root, "bounds", "[100,900][980,980]")
        self.assertFalse(ev["found"])


class RelativeChainRuntimeTests(unittest.TestCase):
    def test_resolve_parent_index_chain(self):
        import record_helper as rh

        original = rh.dump_ui_cached
        rh.dump_ui_cached = lambda *a, **k: LIST_XML
        rh.dump_ui = lambda *a, **k: LIST_XML
        try:
            locs = {"parent_index": "container=recycler|index=2"}
            chain = [{"type": "parent_index", "enabled": True}]
            out = resolve_locator_chain("emulator-5554", locs, chain, display_name="列表项")
            self.assertTrue(out["ok"])
            self.assertEqual(out["matched_by"], "parent_index")
        finally:
            rh.dump_ui_cached = original

    def test_validate_relative_in_chain(self):
        import record_helper as rh

        original = rh.dump_ui_cached
        rh.dump_ui_cached = lambda *a, **k: LIST_XML
        rh.dump_ui = lambda *a, **k: LIST_XML
        try:
            locs = {
                "text": "不存在",
                "anchor_adjacent": "anchor=用户名|dir=right",
            }
            chain = [
                {"type": "text", "enabled": True},
                {"type": "anchor_adjacent", "enabled": True},
            ]
            out = validate_locators_on_screen("emulator-5554", locs, chain)
            self.assertTrue(out["valid"])
            self.assertEqual(out["matched_by"], "anchor_adjacent")
        finally:
            rh.dump_ui_cached = original


if __name__ == "__main__":
    unittest.main(verbosity=2)
