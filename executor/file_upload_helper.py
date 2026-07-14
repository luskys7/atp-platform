"""Android 文件上传辅助：adb push + 唤起系统文件选择器。"""
import subprocess
import time


def push_file(serial: str, local_path: str, remote_dir: str = "/sdcard/Download") -> str:
    import os

    fp = os.path.abspath(local_path)
    if not os.path.isfile(fp):
        raise FileNotFoundError(f"本地文件不存在: {local_path}")
    remote = f"{remote_dir.rstrip('/')}/{os.path.basename(fp)}"
    r = subprocess.run(
        ["adb", "-s", serial, "push", fp, remote],
        capture_output=True,
        text=True,
        timeout=120,
        check=False,
    )
    if r.returncode != 0:
        raise RuntimeError(f"adb push 失败: {(r.stderr or r.stdout or '').strip()}")
    return remote


def open_file_picker(serial: str, mime: str = "*/*") -> None:
    """唤起系统文件选择器（GET_CONTENT）。"""
    subprocess.run(
        [
            "adb", "-s", serial, "shell", "am", "start",
            "-a", "android.intent.action.GET_CONTENT",
            "-t", mime,
        ],
        capture_output=True,
        text=True,
        timeout=15,
        check=False,
    )
    time.sleep(0.8)


def upload_and_pick(serial: str, local_path: str, open_picker: bool = True) -> str:
    remote = push_file(serial, local_path)
    if open_picker:
        open_file_picker(serial)
    return remote
