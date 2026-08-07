#!/usr/bin/env python3
"""TestFlow 访客启动器：配置中心地址并拉起本机执行器。"""

from __future__ import annotations

import configparser
import os
import socket
import subprocess
import sys
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk
from typing import Optional
from urllib.parse import urlparse
from urllib.request import urlopen

APP_TITLE = "TestFlow 本机执行器"
INI_NAME = "executor_launcher.ini"
SERVER_FLAG = "--executor-server"


def app_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def ini_path() -> Path:
    return app_dir() / INI_NAME


def guess_lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip and not ip.startswith("127."):
            return ip
    except OSError:
        pass
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return "127.0.0.1"


def load_config() -> dict:
    cfg = {
        "platform_url": "http://127.0.0.1:8080",
        "port": "9002",
        "public_url": f"http://{guess_lan_ip()}:9002",
    }
    path = ini_path()
    if not path.exists():
        return cfg
    parser = configparser.ConfigParser()
    try:
        parser.read(path, encoding="utf-8")
        sec = parser["launcher"] if parser.has_section("launcher") else {}
        for key in cfg:
            if key in sec and str(sec[key]).strip():
                cfg[key] = str(sec[key]).strip()
    except Exception:
        pass
    return cfg


def save_config(platform_url: str, port: str, public_url: str) -> None:
    parser = configparser.ConfigParser()
    parser["launcher"] = {
        "platform_url": platform_url.strip(),
        "port": str(port).strip(),
        "public_url": public_url.strip(),
    }
    with ini_path().open("w", encoding="utf-8") as f:
        parser.write(f)


def validate_http_url(url: str, label: str) -> Optional[str]:
    u = (url or "").strip()
    if not u:
        return f"{label}不能为空"
    parsed = urlparse(u)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        return f"{label}须为 http(s)://主机:端口 形式"
    return None


def check_platform(url: str, timeout: float = 4.0) -> tuple[bool, str]:
    base = url.rstrip("/")
    try:
        with urlopen(f"{base}/api/v1/health", timeout=timeout) as resp:
            if 200 <= resp.status < 300:
                return True, "中心可达"
            return False, f"中心返回 HTTP {resp.status}"
    except Exception as e:
        try:
            with urlopen(f"{base}/health", timeout=timeout) as resp:
                if 200 <= resp.status < 300:
                    return True, "中心可达"
        except Exception:
            pass
        return False, f"无法访问中心: {e}"


def port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.3)
        return s.connect_ex(("127.0.0.1", port)) == 0


def run_executor_server() -> None:
    """以服务模式启动（被 GUI 子进程调用，或直接调试）。"""
    # 保证可导入同目录模块
    root = str(app_dir())
    if root not in sys.path:
        sys.path.insert(0, root)
    # frozen 时 PyInstaller 解压目录
    meipass = getattr(sys, "_MEIPASS", None)
    if meipass and meipass not in sys.path:
        sys.path.insert(0, meipass)

    import logging

    import uvicorn

    logging.basicConfig(level=logging.INFO)
    port = int(os.environ.get("EXECUTOR_PORT", "9002"))
    try:
        from platform_sync import start_background_sync

        start_background_sync(port)
    except Exception as e:
        logging.getLogger("atp-executor").warning("platform sync disabled: %s", e)

    from main import app

    uvicorn.run(app, host="0.0.0.0", port=port, ws="websockets")


class LauncherApp:
    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title(APP_TITLE)
        self.root.geometry("560x360")
        self.root.minsize(520, 320)

        self.proc: Optional[subprocess.Popen] = None
        self._monitor: Optional[threading.Thread] = None

        cfg = load_config()
        self.var_platform = tk.StringVar(value=cfg["platform_url"])
        self.var_port = tk.StringVar(value=cfg["port"])
        self.var_public = tk.StringVar(value=cfg["public_url"])
        self.var_status = tk.StringVar(value="已停止")

        self._build_ui()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def _build_ui(self) -> None:
        pad = {"padx": 12, "pady": 6}
        frm = ttk.Frame(self.root, padding=12)
        frm.pack(fill=tk.BOTH, expand=True)

        ttk.Label(frm, text="中心平台地址（后端 8080，勿填前端 3000）").grid(row=0, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.var_platform, width=56).grid(row=1, column=0, columnspan=2, sticky="ew", **pad)

        ttk.Label(frm, text="本机端口").grid(row=2, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.var_port, width=16).grid(row=3, column=0, sticky="w", **pad)

        ttk.Label(frm, text="本机对外地址（须局域网 IP，供中心访问）").grid(row=4, column=0, sticky="w")
        row_pub = ttk.Frame(frm)
        row_pub.grid(row=5, column=0, columnspan=2, sticky="ew", **pad)
        ttk.Entry(row_pub, textvariable=self.var_public).pack(side=tk.LEFT, fill=tk.X, expand=True)
        ttk.Button(row_pub, text="填入本机IP", command=self.fill_lan_ip, width=12).pack(side=tk.LEFT, padx=(8, 0))

        ttk.Label(frm, textvariable=self.var_status, foreground="#0369A1").grid(
            row=6, column=0, columnspan=2, sticky="w", pady=(10, 4)
        )

        btns = ttk.Frame(frm)
        btns.grid(row=7, column=0, columnspan=2, sticky="ew", pady=(8, 0))
        self.btn_start = ttk.Button(btns, text="启动执行器", command=self.start_or_restart)
        self.btn_start.pack(side=tk.LEFT)
        self.btn_stop = ttk.Button(btns, text="停止", command=self.stop_executor, state=tk.DISABLED)
        self.btn_stop.pack(side=tk.LEFT, padx=(8, 0))
        ttk.Button(btns, text="检测中心", command=self.probe_platform).pack(side=tk.LEFT, padx=(8, 0))

        tip = (
            "说明：保持本窗口打开；约 10 秒后手机会出现在平台「设备管理」。\n"
            "请本机已安装 ADB 并开启手机 USB 调试。不要与本机已运行的 start-local 抢同一端口。"
        )
        ttk.Label(frm, text=tip, wraplength=520, foreground="#64748B").grid(
            row=8, column=0, columnspan=2, sticky="w", pady=(16, 0)
        )

        frm.columnconfigure(0, weight=1)

    def fill_lan_ip(self) -> None:
        try:
            port = int(self.var_port.get().strip() or "9002")
        except ValueError:
            port = 9002
        self.var_public.set(f"http://{guess_lan_ip()}:{port}")

    def probe_platform(self) -> None:
        err = validate_http_url(self.var_platform.get(), "中心平台地址")
        if err:
            messagebox.showwarning(APP_TITLE, err)
            return
        ok, msg = check_platform(self.var_platform.get())
        if ok:
            messagebox.showinfo(APP_TITLE, msg)
        else:
            messagebox.showerror(APP_TITLE, msg)

    def _read_fields(self) -> Optional[tuple[str, int, str]]:
        platform = self.var_platform.get().strip()
        public = self.var_public.get().strip()
        err = validate_http_url(platform, "中心平台地址")
        if err:
            messagebox.showwarning(APP_TITLE, err)
            return None
        err = validate_http_url(public, "本机对外地址")
        if err:
            messagebox.showwarning(APP_TITLE, err)
            return None
        if "127.0.0.1" in public or "localhost" in public.lower():
            if not messagebox.askyesno(
                APP_TITLE,
                "本机对外地址使用了 127.0.0.1/localhost，中心机通常无法访问。\n仍要继续吗？",
            ):
                return None
        try:
            port = int(self.var_port.get().strip())
            if not (1 <= port <= 65535):
                raise ValueError
        except ValueError:
            messagebox.showwarning(APP_TITLE, "本机端口须为 1–65535 的整数")
            return None
        return platform, port, public

    def start_or_restart(self) -> None:
        fields = self._read_fields()
        if not fields:
            return
        platform, port, public = fields
        save_config(platform, str(port), public)

        if self.proc and self.proc.poll() is None:
            self._kill_proc()
            time.sleep(0.8)

        if port_in_use(port):
            messagebox.showerror(
                APP_TITLE,
                f"端口 {port} 已被占用。请更换端口，或先结束本机旧执行器 / start-local。",
            )
            return

        ok, msg = check_platform(platform)
        if not ok:
            if not messagebox.askyesno(APP_TITLE, f"{msg}\n\n仍要启动本机执行器吗？"):
                return

        env = os.environ.copy()
        env["PLATFORM_URL"] = platform
        env["ATP_BACKEND_URL"] = platform
        env["EXECUTOR_PORT"] = str(port)
        env["EXECUTOR_PUBLIC_URL"] = public

        try:
            if getattr(sys, "frozen", False):
                cmd = [sys.executable, SERVER_FLAG]
            else:
                main_py = app_dir() / "main.py"
                cmd = [sys.executable, str(main_py)]
            self.proc = subprocess.Popen(
                cmd,
                cwd=str(app_dir()),
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except Exception as e:
            messagebox.showerror(APP_TITLE, f"启动失败: {e}")
            return

        self.btn_start.configure(text="重新启动")
        self.btn_stop.configure(state=tk.NORMAL)
        self.var_status.set(f"启动中… PID={self.proc.pid}  端口={port}")
        self._monitor = threading.Thread(target=self._wait_ready, args=(port,), daemon=True)
        self._monitor.start()

    def _wait_ready(self, port: int) -> None:
        for _ in range(40):
            if self.proc is None or self.proc.poll() is not None:
                self.root.after(0, lambda: self.var_status.set("启动失败（进程已退出）"))
                return
            if port_in_use(port):
                self.root.after(
                    0,
                    lambda: self.var_status.set(
                        f"运行中 PID={self.proc.pid}  http://127.0.0.1:{port}"
                    ),
                )
                return
            time.sleep(0.25)
        self.root.after(0, lambda: self.var_status.set("启动超时，请检查日志或端口"))

    def _kill_proc(self) -> None:
        if not self.proc:
            return
        try:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()
        except Exception:
            pass
        self.proc = None

    def stop_executor(self) -> None:
        self._kill_proc()
        self.btn_start.configure(text="启动执行器")
        self.btn_stop.configure(state=tk.DISABLED)
        self.var_status.set("已停止")

    def on_close(self) -> None:
        if self.proc and self.proc.poll() is None:
            if not messagebox.askyesno(APP_TITLE, "关闭窗口将停止本机执行器，确定吗？"):
                return
            self._kill_proc()
        self.root.destroy()

    def run(self) -> None:
        self.root.mainloop()


def main() -> None:
    if SERVER_FLAG in sys.argv:
        # 去掉 flag，避免干扰
        sys.argv = [a for a in sys.argv if a != SERVER_FLAG]
        run_executor_server()
        return
    LauncherApp().run()


if __name__ == "__main__":
    main()
