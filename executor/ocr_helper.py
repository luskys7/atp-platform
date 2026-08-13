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
    """截图 OCR，返回点击附近文本（按 bbox 近点匹配，合并同行完整短句）。"""
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
    tokens: list[dict] = []
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
        tokens.append({
            "text": raw,
            "dist": dist,
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "cx": cx, "cy": cy,
            "h": h,
            "line_num": int(data.get("line_num", [0])[i] or 0),
            "block_num": int(data.get("block_num", [0])[i] or 0),
            "par_num": int(data.get("par_num", [0])[i] or 0),
        })

    if not tokens:
        return {"text": "", "source": "ocr_empty", "all_lines": []}

    near = [t for t in tokens if t["dist"] <= radius]
    if not near:
        return {"text": "", "source": "ocr_empty", "all_lines": []}

    # 选距离最近的 token，再合并同一视觉行的词，得到完整短句 + 并集 bounds
    seed = min(near, key=lambda t: (t["dist"], len(t["text"])))
    same_line = []
    for t in tokens:
        same_block = (
            t["block_num"] == seed["block_num"]
            and t["par_num"] == seed["par_num"]
            and t["line_num"] == seed["line_num"]
        )
        # tesseract 行号不准时，用垂直重叠兜底
        vert_close = abs(t["cy"] - seed["cy"]) <= max(12, int(0.6 * max(seed["h"], t["h"])))
        if same_block or vert_close:
            # 同行且水平不要太远（避免并到整屏无关字）
            if abs(t["cx"] - seed["cx"]) <= max(radius * 3, 420):
                same_line.append(t)
    if not same_line:
        same_line = [seed]
    same_line.sort(key=lambda t: (t["x1"], t["y1"]))

    # 去重拼接
    parts = []
    for t in same_line:
        if not parts or parts[-1] != t["text"]:
            parts.append(t["text"])
    best_text = "".join(parts) if any("\u4e00" <= ch <= "\u9fff" for ch in "".join(parts)) else " ".join(parts)
    x1 = min(t["x1"] for t in same_line)
    y1 = min(t["y1"] for t in same_line)
    x2 = max(t["x2"] for t in same_line)
    y2 = max(t["y2"] for t in same_line)
    bounds = f"[{x1},{y1}][{x2},{y2}]"
    near_lines = []
    for t in sorted(near, key=lambda z: z["dist"])[:8]:
        if t["text"] not in near_lines:
            near_lines.append(t["text"])
    if best_text:
        return {
            "text": best_text.strip(),
            "source": "ocr_near",
            "distance": seed["dist"],
            "bounds": bounds,
            "image_width": int(img.width),
            "image_height": int(img.height),
            "all_lines": near_lines,
        }
    # unreachable — kept for clarity
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
