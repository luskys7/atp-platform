"""可视化步骤：自定义脚本（Python / Java）执行辅助。"""

from __future__ import annotations

import base64
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any, Optional


def run_custom_script(
    lang: str,
    code: str,
    *,
    serial: str = "",
    vars_dict: Optional[dict] = None,
    timeout: int = 120,
) -> str:
    """执行用户自定义脚本。失败抛 RuntimeError。"""
    language = (lang or "python").strip().lower()
    source = (code or "").strip()
    if not source:
        raise RuntimeError("自定义脚本内容为空")

    env = os.environ.copy()
    if serial:
        env["ATP_DEVICE_SERIAL"] = serial
    if vars_dict is not None:
        import json as _json
        env["ATP_VARS_JSON"] = _json.dumps(vars_dict, ensure_ascii=False)

    if language in ("python", "py"):
        return _run_python(source, env=env, serial=serial, vars_dict=vars_dict or {}, timeout=timeout)
    if language in ("java",):
        return _run_java(source, env=env, timeout=timeout)
    raise RuntimeError(f"不支持的自定义脚本语言: {lang}（仅支持 python / java）")


def run_custom_script_b64(lang: str, code_b64: str, serial: str = "", vars_dict: Optional[dict] = None, timeout: int = 120) -> str:
    raw = base64.b64decode(code_b64 or "").decode("utf-8", errors="replace")
    return run_custom_script(lang, raw, serial=serial, vars_dict=vars_dict, timeout=timeout)


def _run_python(source: str, *, env: dict, serial: str, vars_dict: dict, timeout: int) -> str:
    """在隔离命名空间中 exec，注入常用上下文。"""
    local_ns: dict[str, Any] = {
        "serial": serial,
        "os": os,
        "sys": sys,
        "subprocess": subprocess,
        "_VARS": vars_dict,
        "var": lambda name, default="": vars_dict.get(name, default),
        "set_var": lambda name, value: vars_dict.__setitem__(str(name), str(value)),
        "print": print,
    }
    try:
        compiled = compile(source, "<atp_custom_script>", "exec")
        exec(compiled, {"__name__": "__atp_custom__"}, local_ns)
    except Exception as e:
        raise RuntimeError(f"Python 自定义脚本执行失败: {e}") from e
    return "python custom script ok"


def _run_java(source: str, *, env: dict, timeout: int) -> str:
    java = _which("java")
    javac = _which("javac")
    if not java or not javac:
        raise RuntimeError("执行 Java 自定义脚本需要本机安装 JDK（javac/java 可用）")

    with tempfile.TemporaryDirectory(prefix="atp_java_") as tmp:
        tmp_path = Path(tmp)
        class_name, java_src = _wrap_java_source(source)
        src_file = tmp_path / f"{class_name}.java"
        src_file.write_text(java_src, encoding="utf-8")
        compile_proc = subprocess.run(
            [javac, "-encoding", "UTF-8", src_file.name],
            cwd=str(tmp_path),
            capture_output=True,
            text=True,
            timeout=min(timeout, 60),
            env=env,
        )
        if compile_proc.returncode != 0:
            err = (compile_proc.stderr or compile_proc.stdout or "").strip()
            raise RuntimeError(f"Java 编译失败:\n{err[:2000]}")

        run_proc = subprocess.run(
            [java, "-Dfile.encoding=UTF-8", class_name],
            cwd=str(tmp_path),
            capture_output=True,
            text=True,
            timeout=timeout,
            env=env,
        )
        out = (run_proc.stdout or "").strip()
        err = (run_proc.stderr or "").strip()
        if out:
            print(out)
        if run_proc.returncode != 0:
            raise RuntimeError(f"Java 运行失败 (code={run_proc.returncode}):\n{(err or out)[:2000]}")
        if err:
            print(err)
        return "java custom script ok"


def _wrap_java_source(source: str) -> tuple[str, str]:
    """用户可写完整 class，或只写方法体（自动包装 AtpCustomScript.main）。"""
    text = source.strip()
    m = re.search(r"\bpublic\s+class\s+([A-Za-z_][A-Za-z0-9_]*)\b", text)
    if m:
        return m.group(1), text
    m2 = re.search(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)\b", text)
    if m2:
        return m2.group(1), text

    class_name = "AtpCustomScript"
    body = text
    # 缩进用户代码
    indented = "\n".join(("        " + line) if line.strip() else "" for line in body.splitlines())
    wrapped = f"""public class {class_name} {{
    public static void main(String[] args) throws Exception {{
{indented}
    }}
}}
"""
    return class_name, wrapped


def _which(cmd: str) -> Optional[str]:
    from shutil import which
    path = which(cmd)
    if path:
        return path
    # Windows 常见：JAVA_HOME
    home = os.environ.get("JAVA_HOME", "").strip().strip('"')
    if home:
        candidate = Path(home) / "bin" / (cmd + (".exe" if os.name == "nt" else ""))
        if candidate.exists():
            return str(candidate)
    return None
