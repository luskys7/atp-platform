"""ATP 控件池运行时辅助模块 — 脚本中通过 from atp_controls import get, find 使用"""

import json
import os
from typing import Any, Optional, Tuple

_cache: Optional[dict] = None


def _load() -> dict:
    global _cache
    if _cache is None:
        raw = os.environ.get("ATP_CONTROLS_JSON", "{}")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            data = {}
        _cache = data.get("elements", {})
    return _cache


def reload() -> None:
    """强制重新加载控件映射（测试用）"""
    global _cache
    _cache = None
    _load()


def find(element_name: str) -> Optional[dict[str, Any]]:
    """返回完整控件信息 dict，含 locator_type / locator_value / source"""
    return _load().get(element_name)


def get(element_name: str, default: Optional[str] = None) -> Optional[str]:
    """返回定位表达式 locator_value"""
    ctrl = find(element_name)
    if not ctrl:
        return default
    return ctrl.get("locator_value")


def get_locator(element_name: str) -> Tuple[str, str]:
    """返回 (locator_type, locator_value) 元组"""
    ctrl = find(element_name)
    if not ctrl:
        raise KeyError(f"控件未找到: {element_name}，请检查私有绑定或控件池")
    return ctrl["locator_type"], ctrl["locator_value"]


def list_elements() -> list[str]:
    return list(_load().keys())


def ai_find(description: str) -> Tuple[str, str]:
    """运行时 AI 定位：基于 uiautomator dump 匹配文本/描述"""
    import os
    from ai_locator import locate

    serial = os.environ.get("ATP_DEVICE_SERIAL", "")
    platform = os.environ.get("ATP_PLATFORM", "android")
    app_package = os.environ.get("ATP_APP_PACKAGE", "")
    result = locate(serial, platform, description, app_package)
    return result["locator_type"], result["locator_value"]
