"""本地执行器脚本冒烟：弱网 + 崩溃捕获（无真机时用空 serial，仅验证 import 与 marker 输出）"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

EXECUTOR = Path(__file__).resolve().parent.parent / "executor"
SCRIPT = '''
import os, time, json as _json
serial = os.environ.get("ATP_DEVICE_SERIAL", "")
app_package = os.environ.get("ATP_APP_PACKAGE", "")
_VARS = _json.loads(os.environ.get("ATP_VARS_JSON", "{}"))
HUMAN_DELAY = False

def apply_network_profile(profile="2g"):
    from network_helper import apply_network_profile as _apply, network_delay_seconds
    for msg in _apply(serial, profile):
        print(msg)
    delay = network_delay_seconds(profile)
    if delay > 0:
        time.sleep(min(delay, 0.1))

def reset_network_profile():
    from network_helper import reset_network_profile as _reset
    for msg in _reset(serial):
        print(msg)

def capture_crash_now():
    from crash_helper import capture_crash_logs
    try:
        data = capture_crash_logs(serial, app_package, tail=50)
    except Exception as e:
        data = {"fatal_lines": [], "anr_lines": [], "error": str(e), "skipped": True}
    print("ATP_CRASH_LOG:" + _json.dumps(data, ensure_ascii=False)[:8000])
    print("Crash check OK")

apply_network_profile("2g")
time.sleep(0.1)
capture_crash_now()
reset_network_profile()
print("Batch9 executor smoke finished")
'''

def main():
    env = os.environ.copy()
    env["PYTHONPATH"] = str(EXECUTOR) + os.pathsep + env.get("PYTHONPATH", "")
    env["ATP_DEVICE_SERIAL"] = env.get("ATP_DEVICE_SERIAL", "127.0.0.1:6555")
    env["ATP_APP_PACKAGE"] = "com.android.settings"
    env["ATP_VARS_JSON"] = json.dumps({"WAIT_MULTIPLIER": "2.5", "WAIT_TEMPLATE": "weak_network"})
    with tempfile.NamedTemporaryFile("w", suffix=".py", delete=False, encoding="utf-8") as f:
        f.write(SCRIPT)
        path = f.name
    try:
        r = subprocess.run([sys.executable, path], capture_output=True, text=True, timeout=60, env=env)
        out = (r.stdout or "") + (r.stderr or "")
        print(out)
        checks = {
            "network profile": "network profile" in out.lower() or "2g" in out,
            "ATP_CRASH_LOG": "ATP_CRASH_LOG:" in out,
            "reset network": "reset" in out.lower(),
            "exit 0": r.returncode == 0,
        }
        failed = [k for k, v in checks.items() if not v]
        if failed:
            print("FAIL:", failed)
            sys.exit(1)
        print("PASS: executor script smoke")
    finally:
        os.unlink(path)

if __name__ == "__main__":
    main()
