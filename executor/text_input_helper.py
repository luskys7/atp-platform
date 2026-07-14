"""设备文本输入：先点击用户坐标，再对聚焦控件写入；校验失败时多重兜底。"""

import logging
import re
import subprocess
import time
import xml.etree.ElementTree as ET

from adb_client import adb_shell, note_adb_interaction, note_u2_session

logger = logging.getLogger("atp-text-input")

UI_DUMP_REMOTE = "/data/local/tmp/atp_ui_dump.xml"
INPUT_CLASS_HINTS = ("EditText", "AutoComplete", "SearchAutoComplete", "ExtractEditText")


def _run(serial: str, *args, timeout=15) -> subprocess.CompletedProcess:
    return adb_shell(serial, *args, timeout=timeout)


def _tap(serial: str, x: int, y: int) -> None:
    note_adb_interaction(serial)
    adb_shell(serial, "input", "tap", str(x), str(y), timeout=10)


def _parse_bounds(bounds: str) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    return tuple(int(g) for g in m.groups())


def _point_in_bounds(x: int, y: int, bounds: str) -> bool:
    b = _parse_bounds(bounds)
    if not b:
        return False
    l, t, r, bot = b
    return l <= x <= r and t <= y <= bot


def _pull_ui_dump(serial: str) -> str | None:
    """仅读缓存 UI，禁止同步 uiautomator dump（与 u2 冲突会导致设备崩溃）。"""
    try:
        from record_helper import dump_ui_cached
        xml = dump_ui_cached(serial, allow_stale=True, blocking=False)
        if xml:
            return xml
    except Exception:
        pass
    return None


def _is_input_like(node: ET.Element) -> bool:
    cls = node.get("class") or ""
    if any(hint in cls for hint in INPUT_CLASS_HINTS):
        return True
    rid = (node.get("resource-id") or "").lower()
    if any(k in rid for k in ("search", "edit", "input", "query", "keyword")):
        return True
    if node.get("focused") == "true" and node.get("focusable") == "true":
        return True
    return False


def _node_text(node: ET.Element) -> str:
    return (node.get("text") or node.get("content-desc") or "").strip()


def _find_input_node(xml: str, x: int | None = None, y: int | None = None) -> ET.Element | None:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None

    focused_input = None
    point_hits: list[ET.Element] = []
    any_input = None

    for node in root.iter("node"):
        if not _is_input_like(node):
            continue
        if any_input is None:
            any_input = node
        if node.get("focused") == "true":
            focused_input = node
        if x is not None and y is not None and _point_in_bounds(int(x), int(y), node.get("bounds") or ""):
            point_hits.append(node)

    if focused_input is not None:
        return focused_input
    if point_hits:
        if len(point_hits) == 1:
            return point_hits[0]

        def area(n: ET.Element) -> int:
            b = _parse_bounds(n.get("bounds") or "")
            if not b:
                return 10 ** 9
            l, t, r, bot = b
            return (r - l) * (bot - t)

        return min(point_hits, key=area)
    return any_input


def _read_text_u2(serial: str) -> str:
    try:
        import uiautomator2 as u2
    except ImportError:
        return ""
    note_u2_session(serial)
    try:
        d = u2.connect(serial)
        focused = d(focused=True)
        if not focused.exists:
            return ""
        try:
            return (focused.get_text() or "").strip()
        except Exception:
            return (focused.info.get("text") or "").strip()
    except Exception:
        return ""
    finally:
        note_u2_session(serial)
        time.sleep(0.15)


def _text_matches(expected: str, actual: str) -> bool:
    if not expected:
        return bool(actual)
    if not actual:
        return False
    if expected == actual or expected in actual or actual in expected:
        return True
    exp = expected.strip()
    act = actual.strip()
    if not exp or not act:
        return False
    min_len = max(1, len(exp) // 2)
    return act.startswith(exp[:min_len]) or exp.startswith(act[:min_len])


def _verify_text(serial: str, expected: str, x: int | None, y: int | None, *, retries: int = 4) -> bool:
    for attempt in range(retries):
        actual = _read_text_u2(serial)
        if not actual:
            xml = _pull_ui_dump(serial)
            if xml:
                node = _find_input_node(xml, x, y)
                if node is not None:
                    actual = _node_text(node)
        if _text_matches(expected, actual):
            return True
        if attempt < retries - 1:
            time.sleep(0.28)
    return False


def _verify_inconclusive(serial: str, expected: str, x: int | None, y: int | None) -> bool:
    """写入已成功但 adb 繁忙导致读不到文本时，避免误报 400。"""
    actual = _read_text_u2(serial)
    if actual and not _text_matches(expected, actual):
        return False
    xml = _pull_ui_dump(serial)
    if xml:
        node = _find_input_node(xml, x, y)
        if node is not None:
            node_txt = _node_text(node)
            if node_txt and not _text_matches(expected, node_txt):
                return False
    return True


def _try_u2_input(serial: str, text: str) -> bool:
    try:
        import uiautomator2 as u2
    except ImportError:
        return False
    note_u2_session(serial)
    try:
        d = u2.connect(serial)
        d.set_fastinput_ime(False)
        focused = d(focused=True)
        if not focused.exists:
            logger.warning("u2: no focused element after tap")
            return False
        try:
            focused.clear_text()
        except Exception:
            pass
        focused.set_text(text)
        time.sleep(0.35)
        return True
    except Exception as e:
        logger.warning("u2 input failed: %s", e)
        return False
    finally:
        note_u2_session(serial)
        time.sleep(0.25)


def _set_clipboard(serial: str, text: str) -> bool:
    fd, local_path = tempfile.mkstemp(suffix=".txt")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(text)
        push = subprocess.run(
            ["adb", "-s", serial, "push", local_path, "/data/local/tmp/atp_input.txt"],
            capture_output=True, timeout=20, check=False,
        )
        if push.returncode != 0:
            return False
        r = _run(
            serial,
            "sh", "-c",
            "cmd clipboard set-text \"$(cat /data/local/tmp/atp_input.txt)\"",
        )
        return r.returncode == 0
    except OSError as e:
        logger.warning("clipboard failed: %s", e)
        return False
    finally:
        try:
            os.unlink(local_path)
        except OSError:
            pass


def _try_clipboard_paste(serial: str, text: str) -> bool:
    if not _set_clipboard(serial, text):
        return False
    _run(serial, "input", "keycombination", "113", "29")  # CTRL+A
    time.sleep(0.08)
    _run(serial, "input", "keyevent", "279")  # PASTE
    time.sleep(0.35)
    return True


def _escape_adb_input(text: str) -> str:
    """adb input text 仅适合 ASCII；空格与部分符号需转义。"""
    out = []
    for ch in text:
        if ch == " ":
            out.append("%s")
        elif ch in "\\&|;<>^":
            out.append("\\" + ch)
        elif ord(ch) < 128:
            out.append(ch)
        else:
            return ""
    return "".join(out)


def _try_adb_direct_input(serial: str, text: str) -> bool:
    escaped = _escape_adb_input(text)
    if not escaped:
        return False
    r = adb_shell(serial, "input", "text", escaped, timeout=15)
    time.sleep(0.25)
    return r.returncode == 0


def adb_input_text(serial: str, text: str, focus_x: int | None = None, focus_y: int | None = None) -> bool:
    if not text or not serial:
        return False

    if focus_x is not None and focus_y is not None:
        _tap(serial, int(focus_x), int(focus_y))
        time.sleep(0.4)

    writers = [
        ("u2", lambda: _try_u2_input(serial, text)),
        ("clipboard", lambda: _try_clipboard_paste(serial, text)),
        ("adb", lambda: _try_adb_direct_input(serial, text)),
    ]

    for name, write in writers:
        try:
            if not write():
                continue
            if _verify_text(serial, text, focus_x, focus_y):
                logger.info("%s input verified", name)
                return True
            if _verify_inconclusive(serial, text, focus_x, focus_y):
                logger.warning("%s input verify inconclusive, accept write", name)
                return True
            logger.warning("%s wrote but verify mismatch", name)
        except Exception as e:
            logger.warning("%s input error: %s", name, e)
    return False
