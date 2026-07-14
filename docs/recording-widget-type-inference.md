# record_helper.py widget_type 推断补丁设计

> **版本**：v1.0  
> **目标 Sprint**：Sprint 3（P0-C1 全量业务操作识别）  
> **文件**：`executor/record_helper.py`  
> **关联 API**：`POST /api/v1/device/inspect-point`、`POST /api/v1/operation-records/{id}/inspect`

---

## 1. 目标

在 `inspect_point()` 返回中增加 `widget_type` 字段，使录制阶段能区分：

| widget_type | 文档诉求 | 录制步骤 type（finish 后） |
|-------------|----------|---------------------------|
| `click` | 普通点击 | click |
| `select` | 下拉选择 | select |
| `confirm_dialog` | 弹窗确认 | confirm_dialog / dismiss_popup |
| `input` | 文本输入 | input |
| `rich_text` | 富文本编辑 | input（subtype=rich_text） |
| `upload` | 文件上传 | upload |
| `hover` | 鼠标悬浮 | hover / long_press |
| `checkbox` | 复选框 | click |
| `switch` | 开关 | click |
| `unknown` | 无法识别 | tap_xy 降级 |

---

## 2. 推断规则（优先级从高到低）

```
1. class/resource-id 关键词匹配
2. android 属性：checkable, clickable, focusable, long-clickable
3. 父节点/兄弟节点上下文（Dialog 包裹）
4. 默认 click
```

### 2.1 Class 关键词表

```python
_WIDGET_CLASS_RULES: list[tuple[tuple[str, ...], str]] = [
    (("spinner", "dropdown", "appcompatspinner"), "select"),
    (("alertdialog", "dialog", "popupwindow"), "confirm_dialog"),
    (("edittext", "autocompletetextview"), "input"),  # 富文本见 2.3
    (("upload", "filepicker", "document"), "upload"),
    (("checkbox", "checkedtextview"), "checkbox"),
    (("switch", "toggle", "switchcompat"), "switch"),
    (("webview",), "webview"),
    (("recyclerview", "listview", "gridview"), "scroll_list"),
]
```

### 2.2 属性规则

| 条件 | widget_type |
|------|-------------|
| `long-clickable=true` 且非 EditText | `hover` |
| `class` 含 EditText 且 `inputType` 含 textMultiLine | `rich_text` |
| 节点在 Dialog 子树内 且 `text` 匹配 确定/OK/确认 | `confirm_dialog` |
| `resource-id` 含 upload/file/picker | `upload` |

### 2.3 inputType 位掩码（Android）

```python
_INPUT_TYPE_TEXT = 0x00000001
_INPUT_TYPE_MULTILINE = 0x00020000

def _is_rich_text(node: ET.Element) -> bool:
    raw = node.get("input-type") or node.get("inputType") or ""
    try:
        v = int(raw, 0)
        return bool(v & _INPUT_TYPE_TEXT) and bool(v & _INPUT_TYPE_MULTILINE)
    except ValueError:
        return "multiline" in raw.lower() or "textMultiLine" in raw
```

---

## 3. 补丁伪代码（可直接合并进 record_helper.py）

```python
# ── 追加到 record_helper.py 顶部常量区 ──

_WIDGET_CLASS_RULES: list[tuple[tuple[str, ...], str]] = [
    (("spinner", "dropdown", "appcompatspinner"), "select"),
    (("alertdialog", "dialog", "popupwindow"), "confirm_dialog"),
    (("edittext", "autocompletetextview"), "input"),
    (("upload", "filepicker", "document"), "upload"),
    (("checkbox", "checkedtextview"), "checkbox"),
    (("switch", "toggle", "switchcompat"), "switch"),
]

_CONFIRM_TEXTS = frozenset({"确定", "确认", "ok", "yes", "同意", "允许", "继续"})


def _class_short(clazz: str) -> str:
    return (clazz or "").split(".")[-1].lower()


def _infer_widget_type(node: ET.Element, root: ET.Element) -> str:
    clazz = _class_short(node.get("class") or "")
    rid = (node.get("resource-id") or "").lower()
    text = (node.get("text") or "").strip().lower()
    desc = (node.get("content-desc") or "").strip().lower()

    # resource-id 优先
    if any(k in rid for k in ("upload", "file", "picker", "attach")):
        return "upload"

    # class 规则
    for keywords, wtype in _WIDGET_CLASS_RULES:
        if any(k in clazz for k in keywords):
            if wtype == "input" and _is_rich_text(node):
                return "rich_text"
            return wtype

    # Dialog 内确认按钮
    if _is_inside_dialog(node, root):
        label = text or desc
        if label in _CONFIRM_TEXTS or any(t in label for t in _CONFIRM_TEXTS):
            return "confirm_dialog"

    # 长按悬浮
    if node.get("long-clickable") == "true" and "edittext" not in clazz:
        return "hover"

    return "click"


def _is_inside_dialog(node: ET.Element, root: ET.Element) -> bool:
    """向上遍历 6 层，是否存在 Dialog 祖先。"""
    current = node
    for _ in range(6):
        parent = _find_parent(root, current)
        if parent is None:
            break
        if "dialog" in _class_short(parent.get("class") or ""):
            return True
        current = parent
    return False


def _find_parent(root: ET.Element, target: ET.Element) -> ET.Element | None:
    for parent in root.iter():
        for child in list(parent):
            if child is target:
                return parent
    return None


def _build_absolute_xpath(node: ET.Element, root: ET.Element) -> str:
    """从根到节点的绝对 XPath（补充文档要求的第五类定位）。"""
    parts: list[str] = []
    current: ET.Element | None = node
    for _ in range(12):
        if current is None:
            break
        clazz = _class_short(current.get("class") or "") or "*"
        parent = _find_parent(root, current)
        idx = 1
        if parent is not None:
            same = [c for c in list(parent) if _class_short(c.get("class") or "") == clazz]
            if len(same) > 1:
                idx = same.index(current) + 1
        parts.insert(0, f"{clazz}[{idx}]")
        current = parent
    return "/hierarchy/" + "/".join(parts) if parts else ""


# ── 修改 _build_locators：增加 absolute_xpath ──

def _build_locators(node, root, text, desc, rid, clazz) -> dict:
    locators = { ... }  # 现有逻辑不变
    abs_xp = _build_absolute_xpath(node, root)
    if abs_xp and len(abs_xp) < 512:
        locators["absolute_xpath"] = abs_xp
    return locators


# ── 修改 inspect_point：在 result.update 处增加 widget_type ──

def inspect_point(serial: str, x: int, y: int, platform: str = "android") -> dict:
    result = { ..., "widget_type": "unknown", ... }
    ...
    if best and best_node is not None:
        widget_type = _infer_widget_type(best_node, root)
        locators = _build_locators(...)
        result.update({
            ...
            "widget_type": widget_type,
            "suggested_step_type": _map_widget_to_step_type(widget_type),
        })
    return result


def _map_widget_to_step_type(widget_type: str) -> str:
    return {
        "select": "select",
        "confirm_dialog": "confirm_dialog",
        "input": "input",
        "rich_text": "input",
        "upload": "upload",
        "hover": "long_press",
        "checkbox": "click",
        "switch": "click",
        "click": "click",
    }.get(widget_type, "click")
```

---

## 4. Backend 映射（OperationRecordService）

`appendEnrichedClick` / `finalizeVisualSteps` 根据 `widget_type` 输出最终步骤：

```java
private String mapWidgetToStepType(String widgetType) {
    return switch (widgetType != null ? widgetType : "") {
        case "select" -> "select";
        case "confirm_dialog" -> "dismiss_popup";  // 或 confirm_dialog
        case "upload" -> "upload";
        case "rich_text" -> "input";
        case "hover" -> "long_press";
        default -> "click";
    };
}
```

录制中实时 inspect（`inspectDuringRecording`）直接返回 `suggested_step_type` 供前端展示。

---

## 5. 前端展示（DeviceScreen / RecordCaseReview）

```javascript
// appendRecordEvent 时
if (inspect.data.widget_type === 'select') {
  payload.type = 'select'
  payload.display_name = inspect.data.display_name || '下拉选择'
}
if (!inspect.data.valid) {
  payload.locator_valid = false
  ElMessage.warning(`步骤定位待确认：${inspect.data.display_name || '未知控件'}`)
}
```

审阅页步骤 tag 展示 `widget_type`：

```vue
<el-tag size="small">{{ step.widget_type || step.type }}</el-tag>
```

---

## 6. 单元测试用例（verify-batch15 建议）

| 测试 | mock UI XML | 期望 widget_type |
|------|-------------|------------------|
| WH-01 | Spinner bounds 命中 | select |
| WH-02 | AlertDialog + 确定按钮 | confirm_dialog |
| WH-03 | EditText inputType multiline | rich_text |
| WH-04 | resource-id 含 file_upload | upload |
| WH-05 | long-clickable ImageView | hover |
| WH-06 | 普通 TextView clickable | click |
| WH-07 | absolute_xpath 长度 | < 512 且以 /hierarchy/ 开头 |

```python
# verify-batch15 片段
def test_widget_spinner(self):
    xml = """<hierarchy>
      <node class="android.widget.Spinner" text="请选择" bounds="[0,0][100,50]" clickable="true"/>
    </hierarchy>"""
    with patch("record_helper.dump_ui", return_value=xml):
        r = inspect_point("dev", 50, 25)
    self.assertEqual(r["widget_type"], "select")
    self.assertEqual(r["suggested_step_type"], "select")
```

---

## 7. 与文档验收对齐

| 文档要求 | 本补丁覆盖 |
|----------|------------|
| 下拉选择 | select |
| 弹窗确认 | confirm_dialog |
| 文件上传 | upload |
| 鼠标悬浮 | hover → long_press |
| 富文本编辑 | rich_text → input |
| 五类定位参数 | + absolute_xpath |
| 识别率 ≥95% | 配合 recognition_rate 统计 |

---

## 8. 实施顺序

1. `record_helper.py`：`_infer_widget_type` + `absolute_xpath`（纯 Python，可单测）
2. `main.py` inspect-point 响应自动包含新字段
3. `OperationRecordService.enrichRecordedStepsAsync` 使用 `suggested_step_type`
4. 前端 DeviceScreen 实时 inspect 展示
5. 补充 `scripts/verify-batch15.py`
