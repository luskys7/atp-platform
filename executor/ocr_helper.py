"""OCR / 图像兜底定位（离线优先：UI dump → 可选 Tesseract）"""

import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from ai_locator import dump_ui


def _capture_screen(serial: str) -> Path:
    local = Path(tempfile.gettempdir()) / f"atp_screen_{serial}.png"
    remote = "/sdcard/atp_screen.png"
    subprocess.run(["adb", "-s", serial, "shell", "screencap", "-p", remote],
                   capture_output=True, timeout=20)
    subprocess.run(["adb", "-s", serial, "pull", remote, str(local)],
                   capture_output=True, timeout=30)
    subprocess.run(["adb", "-s", serial, "shell", "rm", remote], capture_output=True)
    if not local.exists():
        raise RuntimeError("截图失败")
    return local


def _ui_text_blob(serial: str) -> str:
    xml = dump_ui(serial)
    if not xml:
        return ""
    texts = re.findall(r'text="([^"]*)"', xml)
    descs = re.findall(r'content-desc="([^"]*)"', xml)
    parts = [t for t in texts if t.strip()] + [d for d in descs if d.strip()]
    return "\n".join(parts)


def _ocr_text(image_path: Path) -> str:
    try:
        import pytesseract  # type: ignore
        from PIL import Image  # type: ignore
        return pytesseract.image_to_string(Image.open(image_path), lang="chi_sim+eng")
    except Exception:
        return ""


def ocr_pick_at_point(serial: str, x: int, y: int, radius: int = 180) -> dict:
    """截图 OCR，返回点击附近文本（按 bbox 近点匹配，而非全屏首行）。"""
    shot = _capture_screen(serial)
    try:
        import pytesseract  # type: ignore
        from PIL import Image  # type: ignore
        img = Image.open(shot)
        data = pytesseract.image_to_data(img, lang="chi_sim+eng", output_type=pytesseract.Output.DICT)
    except Exception:
        blob = _ocr_text(shot)
        lines = [ln.strip() for ln in blob.splitlines() if ln.strip()]
        if not lines:
            return {"text": "", "source": "ocr_empty"}
        return {"text": lines[0], "source": "ocr_fallback_line", "all_lines": lines[:8]}

    n = len(data.get("text") or [])
    best_text = ""
    best_dist = 10**9
    near_lines: list[str] = []
    for i in range(n):
        raw = (data["text"][i] or "").strip()
        if not raw or len(raw) < 1:
            continue
        try:
            conf = float(data.get("conf", ["-1"])[i])
        except Exception:
            conf = -1
        if conf >= 0 and conf < 35:
            continue
        left = int(data["left"][i])
        top = int(data["top"][i])
        w = int(data["width"][i])
        h = int(data["height"][i])
        if w <= 0 or h <= 0:
            continue
        x1, y1, x2, y2 = left, top, left + w, top + h
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if x1 <= x <= x2 and y1 <= y <= y2:
            dist = abs(cx - x) + abs(cy - y) // 4
        else:
            dist = abs(cx - x) + abs(cy - y)
        if dist > radius:
            continue
        near_lines.append(raw)
        # 短中文标签（定时/教程）优先
        bonus = 0
        if 1 <= len(raw) <= 8:
            bonus = -20
        key_dist = dist + bonus
        if key_dist < best_dist:
            best_dist = key_dist
            best_text = raw
    if best_text:
        return {
            "text": best_text,
            "source": "ocr_near",
            "distance": best_dist,
            "all_lines": near_lines[:8],
        }
    # 近点无命中时不回退全屏首行，避免误用远处文本
    return {"text": "", "source": "ocr_empty", "all_lines": []}


def find_text(serial: str, query: str, fuzzy: bool = True) -> dict:
    q = (query or "").strip()
    if not q:
        raise ValueError("query 不能为空")

    ui_blob = _ui_text_blob(serial)
    if q in ui_blob:
        return {"source": "ui_dump", "query": q, "found": True}

    if fuzzy and ui_blob:
        q_lower = q.lower()
        for line in ui_blob.splitlines():
            if q_lower in line.lower():
                return {"source": "ui_fuzzy", "query": q, "found": True, "line": line.strip()}

    shot = _capture_screen(serial)
    ocr_blob = _ocr_text(shot)
    if ocr_blob and q in ocr_blob:
        return {"source": "ocr", "query": q, "found": True}

    if fuzzy and ocr_blob:
        for line in ocr_blob.splitlines():
            if q.lower() in line.lower():
                return {"source": "ocr_fuzzy", "query": q, "found": True, "line": line.strip()}

    raise AssertionError(f"OCR/UI 未找到文本: {q}")


def tap_ocr_text(serial: str, query: str):
    xml = dump_ui(serial)
    q = query.strip().lower()
    if xml:
        root = ET.fromstring(xml)
        for node in root.iter("node"):
            text = ((node.get("text") or "") + " " + (node.get("content-desc") or "")).lower()
            if q in text:
                bounds = node.get("bounds") or ""
                nums = [int(n) for n in re.findall(r"\d+", bounds)]
                if len(nums) >= 4:
                    cx, cy = (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2
                    subprocess.run(["adb", "-s", serial, "shell", "input", "tap", str(cx), str(cy)],
                                   capture_output=True, timeout=10)
                    print(f"OCR tap OK: {query} @ ({cx},{cy})")
                    return
    find_text(serial, query)
    raise AssertionError(f"OCR tap 无坐标: {query}")
