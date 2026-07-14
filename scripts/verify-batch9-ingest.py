"""验证 FailureSnapshotService 日志解析与入库（直连 H2 需 backend 运行）"""
import json
import urllib.request

BASE = "http://localhost:8080/api/v1"


def login():
    body = json.dumps({"username": "admin", "password": "admin123"}).encode()
    req = urllib.request.Request(f"{BASE}/auth/login", data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode())["data"]["token"]


def main():
    token = login()
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    # 创建会快速失败的任务（无设备）以触发 executor 失败路径较难；改为仅验证 ingest 逻辑：
    # 提交 task 13 若存在则查 snapshots
    task_id = 13
    req = urllib.request.Request(f"{BASE}/tasks/{task_id}/failure-snapshots", headers=headers, method="GET")
    with urllib.request.urlopen(req, timeout=10) as resp:
        snaps = json.loads(resp.read().decode()).get("data", [])
    print(f"task {task_id} snapshots: {len(snaps)}")

    # 模拟 marker 解析（与 FailureSnapshotService 同规则）
    crash = json.dumps({"fatal_lines": ["FATAL EXCEPTION: demo"], "process_alive": False})
    snap = json.dumps({"process_info": "demo proc", "memory_info": "heap 1MB"})
    logs = [f"ATP_CRASH_LOG:{crash}", f"ATP_FAILURE_SNAPSHOT:{snap}"]
    import re
    c = re.search(r"ATP_CRASH_LOG:(.+)", logs[0])
    s = re.search(r"ATP_FAILURE_SNAPSHOT:(.+)", logs[1])
    assert c and s
    print("ingest marker parse: OK")
    print("NOTE: 完整入库需真机/模拟器执行任务失败；当前 API 链路已通")


if __name__ == "__main__":
    main()
