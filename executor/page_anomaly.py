"""页面异常检测：黑屏、白屏、闪退（进程不存在）"""

import subprocess
import tempfile
from pathlib import Path


class PageAnomalyError(Exception):
    def __init__(self, anomaly_type: str, detail: str):
        super().__init__(f"PAGE_ANOMALY:{anomaly_type}:{detail}")
        self.anomaly_type = anomaly_type
        self.detail = detail


def _adb(serial: str, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["adb", "-s", serial, *args],
        capture_output=True,
        text=True,
        timeout=30,
    )


def is_process_alive(serial: str, app_package: str) -> bool:
    if not app_package:
        return True
    result = _adb(serial, "shell", "pidof", app_package)
    return result.returncode == 0 and bool(result.stdout.strip())


def capture_screenshot(serial: str) -> Path:
    local = Path(tempfile.gettempdir()) / f"atp_screen_{serial}.png"
    proc = subprocess.run(
        ["adb", "-s", serial, "exec-out", "screencap", "-p"],
        capture_output=True,
        timeout=30,
    )
    if proc.returncode != 0 or not proc.stdout:
        raise PageAnomalyError("capture_failed", "无法截取屏幕")
    local.write_bytes(proc.stdout)
    return local


def _analyze_png(path: Path) -> dict:
    """分析截图亮度与色彩分布，无需 Pillow 时用 PNG 字节启发式。"""
    data = path.read_bytes()
    if len(data) < 100:
        return {"avg": 128, "variance": 100, "dark_ratio": 0.5, "bright_ratio": 0.5}

    try:
        from PIL import Image
        import statistics

        img = Image.open(path).convert("L")
        pixels = list(img.getdata())
        if not pixels:
            return {"avg": 128, "variance": 0, "dark_ratio": 0, "bright_ratio": 0}
        avg = statistics.mean(pixels)
        variance = statistics.pvariance(pixels) if len(pixels) > 1 else 0
        dark = sum(1 for p in pixels if p < 15) / len(pixels)
        bright = sum(1 for p in pixels if p > 240) / len(pixels)
        return {"avg": avg, "variance": variance, "dark_ratio": dark, "bright_ratio": bright}
    except ImportError:
        sample = data[100:min(len(data), 50000):3]
        if not sample:
            sample = data
        avg = sum(sample) / len(sample)
        dark = sum(1 for b in sample if b < 20) / len(sample)
        bright = sum(1 for b in sample if b > 235) / len(sample)
        variance = sum((b - avg) ** 2 for b in sample) / len(sample)
        return {"avg": avg, "variance": variance, "dark_ratio": dark, "bright_ratio": bright}


def run_anomaly_check(serial: str, app_package: str = "", check_types: str = "all") -> None:
    """
    检测页面异常，异常时抛出 PageAnomalyError。
    check_types: all | crash | black | white
    """
    types = {t.strip() for t in (check_types or "all").split(",")} if check_types != "all" else {"crash", "black", "white"}

    if "crash" in types or check_types == "all":
        if app_package and not is_process_alive(serial, app_package):
            raise PageAnomalyError("crash", f"应用进程不存在: {app_package}")

    path = capture_screenshot(serial)
    stats = _analyze_png(path)

    if ("black" in types or check_types == "all") and stats["dark_ratio"] > 0.92 and stats["variance"] < 200:
        raise PageAnomalyError("black_screen", f"黑屏检测 dark={stats['dark_ratio']:.2f}")

    if ("white" in types or check_types == "all") and stats["bright_ratio"] > 0.92 and stats["variance"] < 200:
        raise PageAnomalyError("white_screen", f"白屏检测 bright={stats['bright_ratio']:.2f}")

    if stats["variance"] < 5 and stats["dark_ratio"] > 0.85:
        raise PageAnomalyError("black_screen", "画面几乎无变化，疑似黑屏/卡死")

    print(f"Page anomaly check OK avg={stats['avg']:.1f} var={stats['variance']:.1f}")
