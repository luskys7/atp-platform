"""图像相似度比对（PIL 可选，无依赖时降级为尺寸/直方图比对）"""
import subprocess
import tempfile
from pathlib import Path


def _capture_screen(serial: str) -> Path:
    local = Path(tempfile.gettempdir()) / f"atp_img_{serial}.png"
    remote = "/sdcard/atp_img.png"
    subprocess.run(["adb", "-s", serial, "shell", "screencap", "-p", remote],
                   capture_output=True, timeout=20)
    subprocess.run(["adb", "-s", serial, "pull", remote, str(local)],
                   capture_output=True, timeout=30)
    subprocess.run(["adb", "-s", serial, "shell", "rm", remote], capture_output=True)
    if not local.exists():
        raise RuntimeError("截图失败")
    return local


def _load_image(path: str):
    try:
        from PIL import Image  # type: ignore
        return Image.open(path).convert("RGB")
    except Exception as e:
        raise RuntimeError(f"无法加载图像 {path}: {e}") from e


def _histogram_similarity(img1, img2) -> float:
    h1 = img1.histogram()
    h2 = img2.histogram()
    if len(h1) != len(h2):
        return 0.0
    s1 = sum(h1) or 1
    s2 = sum(h2) or 1
    dot = sum(a * b for a, b in zip(h1, h2))
    norm = (sum(a * a for a in h1) ** 0.5) * (sum(b * b for b in h2) ** 0.5)
    return dot / norm if norm else 0.0


def _mse_similarity(img1, img2) -> float:
    w, h = min(img1.size[0], img2.size[0]), min(img1.size[1], img2.size[1])
    if w <= 0 or h <= 0:
        return 0.0
    a = img1.resize((w, h))
    b = img2.resize((w, h))
    pixels1 = list(a.getdata())
    pixels2 = list(b.getdata())
    mse = sum(sum((c1 - c2) ** 2 for c1, c2 in zip(p1, p2)) for p1, p2 in zip(pixels1, pixels2)) / (w * h * 3)
    return max(0.0, 1.0 - min(1.0, mse / 65025.0))


def compare_images(current_path: Path, template_path: str, threshold: float = 0.85,
                   region=None) -> float:
    tpl = _load_image(template_path)
    cur = _load_image(str(current_path))
    if region and len(region) >= 4:
        x, y, w, h = region[:4]
        cur = cur.crop((x, y, x + w, y + h))
    if tpl.size != cur.size:
        cur = cur.resize(tpl.size)
    score = max(_histogram_similarity(tpl, cur), _mse_similarity(tpl, cur))
    if score < threshold:
        raise AssertionError(f"Image similarity {score:.3f} < threshold {threshold}")
    return score


def assert_image_similar(serial: str, template_path: str, threshold: float = 0.85,
                         region_x: int = 0, region_y: int = 0,
                         region_w: int = 0, region_h: int = 0) -> None:
    shot = _capture_screen(serial)
    region = None
    if region_w > 0 and region_h > 0:
        region = (region_x, region_y, region_w, region_h)
    score = compare_images(shot, template_path, threshold, region)
    print(f"Assert image OK: similarity={score:.3f} template={template_path}")
