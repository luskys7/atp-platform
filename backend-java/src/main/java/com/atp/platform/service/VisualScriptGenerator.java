package com.atp.platform.service;

import com.atp.platform.entity.CommonStep;
import com.atp.platform.entity.TestCase;
import com.atp.platform.repository.CommonStepRepository;
import com.atp.platform.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class VisualScriptGenerator {

    private final ObjectMapper objectMapper;
    private final CommonStepRepository commonStepRepository;
    private final TestCaseRepository testCaseRepository;
    private final DataFactoryService dataFactoryService;

    private static final String RUNTIME_HEADER = """
            import os
            import re
            import subprocess
            import time
            import json as _json
            
            from atp_controls import get_locator, ai_find, find
            from step_marker_helper import emit_step_begin, emit_step_end
            
            serial = os.environ.get("ATP_DEVICE_SERIAL", "")
            app_package = os.environ.get("ATP_APP_PACKAGE", "")
            HUMAN_DELAY = os.environ.get("ATP_HUMAN_DELAY", "0") == "1"
            _VARS = _json.loads(os.environ.get("ATP_VARS_JSON", "{}"))
            _CAL = _json.loads(os.environ.get("ATP_CALIBRATION_JSON", "{}"))
            _ASSERT_POLICY = _json.loads(os.environ.get("ATP_ASSERT_POLICY_JSON", "{\\"whitelist\\":[],\\"blacklist\\":[]}"))
            
            
            def _subst_tpl(s):
                if not s:
                    return s
                r = str(s)
                for k, v in _VARS.items():
                    r = r.replace("{{" + k + "}}", str(v))
                return r
            
            
            app_package = _subst_tpl(app_package or "")
            
            
            def warm_ui_cache(serial_no=None, blocking=True):
                from record_helper import warm_ui_cache as _warm
                return _warm(serial_no or serial, blocking=blocking)
            
            
            def _match_pat(pattern, value):
                if not pattern or value is None:
                    return False
                import fnmatch
                return fnmatch.fnmatch(str(value), str(pattern)) or str(pattern) in str(value)
            
            
            def _is_soft_assert(step_type, target=""):
                for rule in _ASSERT_POLICY.get("whitelist", []):
                    tt = rule.get("target_type", "")
                    pat = rule.get("pattern", "")
                    if tt == "assert_type" and _match_pat(pat, step_type):
                        return True
                    if tt in ("element_name", "toast_pattern") and target and _match_pat(pat, target):
                        return True
                return False
            
            
            def _is_forbidden_assert(step_type, target=""):
                for rule in _ASSERT_POLICY.get("blacklist", []):
                    tt = rule.get("target_type", "")
                    pat = rule.get("pattern", "")
                    if tt == "assert_type" and _match_pat(pat, step_type):
                        return True
                    if tt in ("element_name", "toast_pattern") and target and _match_pat(pat, target):
                        return True
                return False
            
            
            def _guard_assert(step_type, target, fn):
                if _is_forbidden_assert(step_type, target):
                    print(f"ATP_ASSERT_BLACKLIST_SKIP:{step_type}:{target}")
                    return
                try:
                    fn()
                except AssertionError as e:
                    if _is_soft_assert(step_type, target):
                        print(f"ATP_SOFT_ASSERT_SKIP:{step_type}:{target}:{e}")
                    else:
                        raise
            
            
            def human_pause(base=0.3):
                import random
                mul = float(_VARS.get("WAIT_MULTIPLIER", "1") or "1")
                base = base * mul
                if HUMAN_DELAY:
                    time.sleep(base + random.uniform(0, 0.5))
                else:
                    time.sleep(base)
            
            
            def var(name, default=""):
                return _VARS.get(name, default)
            
            
            def set_var(name, value):
                _VARS[str(name)] = str(value)
                print(f"ATP_VAR_OUT:{name}={value}")
            
            
            def set_relative_time(offset_minutes=5, confirm=False):
                from time_picker_helper import set_relative_time as _srt
                result = _srt(serial, offset_minutes=int(offset_minutes), confirm=bool(confirm))
                if result:
                    set_var("TIME_HH", f"{int(result.get('hour', 0)):02d}")
                    set_var("TIME_MM", f"{int(result.get('minute', 0)):02d}")
                    set_var("TIME_HM", str(result.get("time") or ""))
                return result
            
            
            def run_custom_script(lang, code_or_b64, *, b64=True, timeout=120, extra_vars=None):
                from custom_script_helper import run_custom_script as _rcs, run_custom_script_b64 as _rcs_b64
                if extra_vars:
                    for _ek, _ev in dict(extra_vars).items():
                        set_var(str(_ek), _subst_tpl(str(_ev)))
                if b64:
                    return _rcs_b64(lang, code_or_b64, serial=serial, vars_dict=_VARS, timeout=timeout)
                return _rcs(lang, code_or_b64, serial=serial, vars_dict=_VARS, timeout=timeout)
            
            
            def calibrate_xy(x, y):
                ox = float(_CAL.get("offset_x", 0) or 0)
                oy = float(_CAL.get("offset_y", 0) or 0)
                sx = float(_CAL.get("scale_x", 1) or 1)
                sy = float(_CAL.get("scale_y", 1) or 1)
                return int(x * sx + ox), int(y * sy + oy)
            
            
            def adb_shell(*args):
                subprocess.run(["adb", "-s", serial, "shell"] + list(args), check=False)
            
            
            def tap_xy(x, y):
                x, y = calibrate_xy(x, y)
                if HUMAN_DELAY:
                    import random
                    x += random.randint(-3, 3)
                    y += random.randint(-3, 3)
                adb_shell("input", "tap", str(int(x)), str(int(y)))
                human_pause(0.2)
            
            
            def tap_bounds(bounds):
                nums = [int(n) for n in re.findall(r"\\d+", bounds or "")]
                if len(nums) >= 4:
                    tap_xy((nums[0] + nums[2]) / 2, (nums[1] + nums[3]) / 2)
            
            
            def _tap_locate_result(result):
                bounds = (result or {}).get("bounds") or ""
                if bounds:
                    tap_bounds(bounds)
                    return True
                loc_type = (result or {}).get("locator_type") or ""
                loc_val = (result or {}).get("locator_value") or ""
                if loc_type == "bounds" and loc_val:
                    tap_bounds(loc_val)
                    return True
                return False
            
            
            def tap_recorded(locators_json, fallback_x=None, fallback_y=None, display_name="", element_name="", chain_json="[]", wait_rule_json="{}"):
                locs = _json.loads(locators_json or "{}")
                chain = _json.loads(chain_json or "[]")
                wait_rule = _json.loads(wait_rule_json or "{}") or None
                if wait_rule and not wait_rule.get("timeout_ms"):
                    wait_rule = None
                try:
                    from locator_runtime import resolve_locator_chain
                    result = resolve_locator_chain(
                        serial, locs, chain,
                        display_name=display_name or element_name,
                        element_name=element_name,
                        wait_rule=wait_rule,
                    )
                    if result.get("ok"):
                        if result.get("bounds"):
                            tap_bounds(result["bounds"])
                            print(f"Tapped via chain [{result.get('matched_by')}]: {display_name or element_name}")
                            return
                        if result.get("x") is not None and result.get("y") is not None:
                            tap_xy(result["x"], result["y"])
                            print(f"Tapped ratio [{result.get('matched_by')}]: {display_name or element_name}")
                            return
                except Exception as _chain_e:
                    print("ATP_LOCATOR_FAIL:" + _json.dumps({"error": "chain_resolve", "detail": str(_chain_e)}, ensure_ascii=False))
                if locs.get("bounds"):
                    tap_bounds(locs["bounds"])
                    print(f"Tapped recorded bounds: {display_name or element_name}")
                    return
                from ai_locator import dump_ui, locate
                import xml.etree.ElementTree as _ET
                xml = dump_ui(serial)
                if xml:
                    root = _ET.fromstring(xml)
                    target_text = (locs.get("text") or display_name or element_name or "").strip()
                    if target_text:
                        for node in root.iter("node"):
                            t = (node.get("text") or "").strip()
                            d = (node.get("content-desc") or "").strip()
                            if t == target_text or d == target_text or target_text in t or target_text in d:
                                b = node.get("bounds") or ""
                                if b:
                                    tap_bounds(b)
                                    print(f"Tapped recorded text: {target_text}")
                                    return
                    rid = (locs.get("resource_id") or locs.get("id") or "").strip()
                    if rid:
                        for node in root.iter("node"):
                            node_rid = (node.get("resource-id") or "").strip()
                            if rid in node_rid or node_rid.endswith(rid):
                                b = node.get("bounds") or ""
                                if b:
                                    tap_bounds(b)
                                    print(f"Tapped recorded id: {rid}")
                                    return
                query = (display_name or locs.get("text") or locs.get("content_desc") or element_name or "").strip()
                if query:
                    try:
                        result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), query, app_package)
                        if _tap_locate_result(result):
                            print(f"Tapped located: {query}")
                            return
                    except Exception:
                        pass
                if fallback_x is not None and fallback_y is not None:
                    tap_xy(fallback_x, fallback_y)
                    print(f"Tapped fallback xy ({fallback_x},{fallback_y}) for {display_name or element_name}")
                    return
                raise RuntimeError(f"无法点击控件: {display_name or element_name}")
            
            
            def tap_element(name, fallback_x=None, fallback_y=None, display_name=""):
                query = (display_name or name or "").strip()
                ctrl = None
                try:
                    ctrl = find(name)
                except Exception:
                    ctrl = None
                if ctrl and isinstance(ctrl, dict) and ctrl.get("locators"):
                    import json as _json2
                    chain = ctrl.get("locator_chain") or []
                    wait_rule = ctrl.get("wait_rule") or {}
                    tap_recorded(
                        _json2.dumps(ctrl["locators"], ensure_ascii=False),
                        fallback_x, fallback_y,
                        display_name or query, name,
                        _json2.dumps(chain, ensure_ascii=False),
                        _json2.dumps(wait_rule, ensure_ascii=False),
                    )
                    return
                try:
                    loc_type, loc_val = get_locator(name)
                    if loc_type == "bounds" and loc_val:
                        tap_bounds(loc_val)
                    else:
                        from ai_locator import locate
                        result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), query or name, app_package)
                        if not _tap_locate_result(result):
                            raise RuntimeError("locator has no bounds")
                except Exception:
                    if fallback_x is not None and fallback_y is not None:
                        tap_xy(fallback_x, fallback_y)
                        print(f"Tapped fallback xy ({fallback_x},{fallback_y}) for {query or name}")
                        return
                    from ai_locator import locate
                    result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), query or name, app_package)
                    if not _tap_locate_result(result):
                        raise ValueError(f"未找到匹配 '{query or name}' 的控件")
                print(f"Tapped element: {query or name}")
            
            
            def select_dropdown(element_name, option_text="", display_name=""):
                tap_element(element_name, display_name=display_name or element_name)
                human_pause(0.6)
                if option_text:
                    from ai_locator import locate
                    result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), option_text, app_package)
                    if not _tap_locate_result(result):
                        raise ValueError(f"未找到下拉选项: {option_text}")
                    print(f"Selected option: {option_text}")
            
            
            def confirm_dialog_action(element_name="", option_text="", display_name=""):
                target = (option_text or display_name or element_name or "确定").strip()
                if element_name:
                    tap_element(element_name, display_name=display_name or target)
                else:
                    from ai_locator import locate
                    result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), target, app_package)
                    if not _tap_locate_result(result):
                        raise ValueError(f"未找到弹窗按钮: {target}")
                print(f"Confirmed dialog: {target}")
            
            
            def tap_upload_control(element_name="", file_path="", display_name=""):
                label = (display_name or element_name or "上传").strip()
                tap_element(element_name or label, display_name=label)
                human_pause(0.5)
                if file_path:
                    from file_upload_helper import upload_and_pick
                    try:
                        remote = upload_and_pick(serial, file_path, open_picker=True)
                        print(f"Upload OK: pushed and opened picker -> {remote}")
                    except Exception as _ue:
                        print(f"Upload fallback: {_ue}")
                human_pause(0.4)
            
            
            def input_text(text):
                safe = text.replace(" ", "%s")
                adb_shell("input", "text", safe)
                print(f"Input text: {text}")
            
            
            def input_rich_text(text):
                lines = (text or "").split("\\n")
                for i, line in enumerate(lines):
                    if line:
                        input_text(line)
                    if i < len(lines) - 1:
                        adb_shell("input", "keyevent", "66")
                print(f"Input rich text ({len(lines)} lines)")
            
            
            def hover_element(element_name, display_name=""):
                label = (display_name or element_name or "").strip()
                try:
                    loc_type, loc_val = get_locator(element_name)
                    if loc_type == "bounds" and loc_val:
                        parts = loc_val.replace("[", "").replace("]", ",").split(",")
                        if len(parts) >= 4:
                            x1, y1, x2, y2 = map(int, parts[:4])
                            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                            adb_shell("input", "swipe", str(cx), str(cy), str(cx), str(cy), "600")
                            print(f"Hover element: {label}")
                            return
                except Exception:
                    pass
                from ai_locator import locate
                result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), label or element_name, app_package)
                bounds = result.get("bounds") or ""
                if bounds:
                    tap_bounds(bounds)
                    print(f"Hover fallback tap: {label}")
                else:
                    raise ValueError(f"无法悬浮控件: {label}")
            
            
            def launch_app(package):
                pkg = _subst_tpl(package or "") or app_package
                if not pkg:
                    print("WARN: launch_app skipped — empty package")
                    return
                if "{{" in pkg and "}}" in pkg:
                    print(f"WARN: launch package still contains unresolved template: {pkg}")
                adb_shell("monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")
                time.sleep(1.0)
                print(f"Launched: {pkg}")
            
            
            def assert_element_exists(name):
                try:
                    get_locator(name)
                    print(f"Assert exists OK: {name}")
                    return True
                except KeyError:
                    from ai_locator import locate
                    result = locate(serial, os.environ.get("ATP_PLATFORM", "android"), name, app_package)
                    if result.get("bounds"):
                        print(f"Assert exists OK (AI): {name}")
                        return True
                    raise AssertionError(f"Element not found: {name}")


            def assert_element_not_exists(name, timeout=3):
                deadline = time.time() + max(1, int(timeout))
                while time.time() < deadline:
                    try:
                        assert_element_exists(name)
                        time.sleep(0.4)
                    except AssertionError:
                        print(f"Assert not exists OK: {name}")
                        return True
                    except Exception:
                        print(f"Assert not exists OK: {name}")
                        return True
                raise AssertionError(f"Element still present: {name}")


            def wait_element(name, timeout=10):
                deadline = time.time() + max(1, int(timeout))
                last_err = None
                while time.time() < deadline:
                    try:
                        assert_element_exists(name)
                        print(f"Wait appear OK: {name}")
                        return True
                    except Exception as e:
                        last_err = e
                        time.sleep(0.5)
                raise TimeoutError(f"Wait appear timeout: {name} ({last_err})")


            def wait_element_gone(name, timeout=10):
                deadline = time.time() + max(1, int(timeout))
                while time.time() < deadline:
                    try:
                        assert_element_exists(name)
                        time.sleep(0.5)
                    except Exception:
                        print(f"Wait disappear OK: {name}")
                        return True
                raise TimeoutError(f"Wait disappear timeout: {name}")


            def clear_input_field(element_name=""):
                if element_name:
                    tap_element(element_name, display_name=element_name)
                    time.sleep(0.2)
                try:
                    import uiautomator2 as u2
                    d = u2.connect(serial)
                    focused = d(focused=True)
                    if focused.exists:
                        focused.clear_text()
                        print(f"Cleared input: {element_name or '(focused)'}")
                        return
                except Exception:
                    pass
                for _ in range(30):
                    adb_shell("input", "keyevent", "67")
                print(f"Cleared input (keyevent): {element_name or '(focused)'}")
            
            
            def assert_text_on_screen(expected):
                from ai_locator import dump_ui
                xml = dump_ui(serial)
                if expected not in xml:
                    raise AssertionError(f"Text not found: {expected}")
                print(f"Assert text OK: {expected}")
            
            
            def check_page_anomaly(check_types="all"):
                from page_anomaly import run_anomaly_check
                run_anomaly_check(serial, app_package, check_types)
            
            
            def assert_app_alive():
                from page_anomaly import is_process_alive
                if app_package and not is_process_alive(serial, app_package):
                    raise AssertionError(f"App process not running: {app_package}")
                print(f"App alive: {app_package}")
            
            
            def clear_app_cache(mode="disk"):
                pkg = _subst_tpl(app_package or os.environ.get("ATP_APP_PACKAGE", "") or "")
                if not pkg:
                    print("clear_app_cache: no package specified")
                    return
                if "{{" in pkg and "}}" in pkg:
                    print(f"clear_app_cache: unresolved package template: {pkg}")
                    return
                if mode in ("memory", "all"):
                    adb_shell("am", "force-stop", pkg)
                    print(f"Force stopped: {pkg}")
                if mode in ("disk", "all"):
                    r = subprocess.run(
                        ["adb", "-s", serial, "shell", "pm", "clear", "--cache-only", pkg],
                        capture_output=True, text=True, timeout=30)
                    if r.returncode != 0:
                        adb_shell("cmd", "package", "trim-caches", pkg, "999G")
                    print(f"Cleared cache ({mode}): {pkg}")
            
            
            def dismiss_popups():
                from popup_helper import dismiss_android_popups
                dismiss_android_popups(serial)
            
            
            def switch_context(mode="auto"):
                from webview_helper import switch_context as _switch
                for msg in _switch(serial, mode):
                    print(msg)
            
            
            def revoke_permissions():
                from execution_context import revoke_android_permissions
                for msg in revoke_android_permissions(serial, app_package):
                    print(f"revoke: {msg}")
            
            
            def assert_toast(expected, timeout=5):
                import time as _t
                from ai_locator import dump_ui
                deadline = _t.time() + float(timeout)
                while _t.time() < deadline:
                    if expected in dump_ui(serial):
                        print(f"Assert toast OK: {expected}")
                        return True
                    _t.sleep(0.5)
                raise AssertionError(f"Toast not found: {expected}")
            
            
            def assert_http(method, url, expected_status=200, body_contains=""):
                import urllib.request
                req = urllib.request.Request(url, method=method.upper())
                with urllib.request.urlopen(req, timeout=15) as resp:
                    code = resp.getcode()
                    body = resp.read().decode("utf-8", errors="ignore")
                if code != int(expected_status):
                    raise AssertionError(f"HTTP status {code} != {expected_status}")
                if body_contains and body_contains not in body:
                    raise AssertionError(f"HTTP body missing: {body_contains}")
                print(f"Assert HTTP OK: {method} {url} -> {code}")
            
            
            def assert_analytics(event_name, props_json="{}", verify_url="", timeout=15):
                from analytics_helper import assert_analytics_event
                props = _json.loads(props_json or "{}")
                for msg in assert_analytics_event(serial, event_name, props, verify_url or "", int(timeout)):
                    print(f"Analytics: {msg}")
            
            
            def assert_composite(conditions_json):
                conds = _json.loads(conditions_json or "[]")
                for c in conds:
                    t = c.get("type", "")
                    if t == "text":
                        assert_text_on_screen(c.get("value", ""))
                    elif t == "exists":
                        assert_element_exists(c.get("value", ""))
                    elif t == "toast":
                        assert_toast(c.get("value", ""), c.get("timeout", 3))
                    elif t == "process":
                        assert_app_alive()
                    elif t == "ocr":
                        assert_ocr_text(c.get("value", ""))
                    else:
                        raise AssertionError(f"Unknown composite condition: {t}")
                print("Composite assert OK")
            
            
            def assert_ocr_text(expected):
                from ocr_helper import find_text
                r = find_text(serial, expected)
                print(f"Assert OCR OK ({r.get('source')}): {expected}")
            
            
            def tap_ocr_text(query):
                from ocr_helper import tap_ocr_text as _tap_ocr
                _tap_ocr(serial, query)
            
            
            def press_system_key(key):
                from system_ops import press_system_key as _press
                _press(serial, key)
                print(f"Pressed key: {key}")
            
            
            def set_clipboard_text(text):
                from system_ops import set_clipboard
                set_clipboard(serial, text)
                print(f"Clipboard set: {text[:40]}...")
            
            
            def assert_clipboard_text(expected):
                from system_ops import assert_clipboard
                assert_clipboard(serial, expected)
                print(f"Assert clipboard OK: {expected}")
            
            
            def wake_device_screen():
                from system_ops import wake_screen
                wake_screen(serial)
                print("Screen woken")
            
            
            def lock_device_screen():
                from system_ops import lock_screen
                lock_screen(serial)
                print("Screen locked")
            
            
            def assert_screen_state(expected):
                from system_ops import assert_screen_state as _assert_screen
                _assert_screen(serial, expected)
                print(f"Assert screen OK: {expected}")
            
            
            def assert_key_response(key):
                from system_ops import assert_key_responded
                assert_key_responded(serial, key, check_screen_change=(key in ("power", "wakeup", "sleep")))
                print(f"Assert key OK: {key}")
            
            
            def assert_volume_level(expected, stream="music", tolerance=1):
                from system_ops import assert_volume
                assert_volume(serial, int(expected), stream, int(tolerance))
                print(f"Assert volume OK: stream={stream} expected={expected}")
            
            
            def assert_volume_changed(direction="up", stream="music", key=""):
                from system_ops import assert_volume_change
                assert_volume_change(serial, direction, stream, key or None, 1)
                print(f"Assert volume change OK: {direction} stream={stream}")
            
            
            def assert_image_similar(template_path, threshold=0.85, region_x=0, region_y=0, region_w=0, region_h=0):
                from image_helper import assert_image_similar as _assert_img
                _assert_img(serial, template_path, float(threshold), int(region_x), int(region_y), int(region_w), int(region_h))
            
            
            def apply_network_profile(profile="2g"):
                from network_helper import apply_network_profile as _apply, network_delay_seconds
                for msg in _apply(serial, profile):
                    print(msg)
                delay = network_delay_seconds(profile)
                if delay > 0:
                    time.sleep(min(delay, 3.0))
            
            
            def reset_network_profile():
                from network_helper import reset_network_profile as _reset
                for msg in _reset(serial):
                    print(msg)
            
            
            def capture_crash_now():
                from crash_helper import capture_crash_logs
                data = capture_crash_logs(serial, app_package)
                print("ATP_CRASH_LOG:" + _json.dumps(data, ensure_ascii=False)[:12000])
                if data.get("fatal_lines") or data.get("anr_lines"):
                    raise AssertionError("检测到崩溃/ANR: " + str(data.get("fatal_lines", [])[:1]))
                print("Crash check OK")
            
            
            def set_device_locale(locale_code="en_us"):
                from locale_helper import set_device_locale as _set_loc
                print(_set_loc(serial, locale_code))
            
            
            def collect_performance_metrics():
                from performance_helper import collect_memory, emit_perf_marker
                data = collect_memory(serial, app_package)
                print(emit_perf_marker(data))
            
            
            def measure_cold_start_ms(max_ms=5000):
                from performance_helper import measure_cold_start, emit_perf_marker
                data = measure_cold_start(serial, app_package)
                print(emit_perf_marker(data))
                if data.get("cold_start_ms") is not None and int(data["cold_start_ms"]) > int(max_ms):
                    raise AssertionError(f"Cold start {data['cold_start_ms']}ms > {max_ms}ms")
                print(f"Cold start OK: {data.get('cold_start_ms')}ms")
            
            
            # === if / else if / else：各自独立块（各有 end_block），链式跳过 ===
            _CF_SKIP = []
            _CF_OPEN = []
            _CF_PENDING = []
            
            
            def _cf_should_skip():
                return any(bool(x) for x in _CF_SKIP)
            
            
            def _cf_break_chain():
                # 同层若出现普通步骤，断开与后续 else if/else 的衔接
                depth = len(_CF_SKIP)
                while _CF_PENDING and _CF_PENDING[-1].get("depth") == depth:
                    _CF_PENDING.pop()
            
            
            def eval_branch_condition(kind="exists", element_name="", expected="", timeout=5, custom="", var_name=""):
                # 条件判断：返回 True/False，不抛断言异常
                k = (kind or "exists").strip().lower()
                name = _subst_tpl(element_name or "")
                exp = _subst_tpl(expected or "")
                custom_txt = _subst_tpl(custom or "")
                var_key = _subst_tpl(var_name or "")
                to = max(1, int(timeout or 5))
                if k in ("", "custom") and custom_txt:
                    if custom_txt in ("控件存在", "exists", "元素存在"):
                        k = "exists"
                    elif custom_txt in ("控件不存在", "not_exists", "元素不存在"):
                        k = "not_exists"
                    elif custom_txt in ("文本包含", "text_contains"):
                        k = "text_contains"
                    elif custom_txt in ("变量等于", "var_equals"):
                        k = "var_equals"
                    elif custom_txt in ("变量不等于", "var_not_equals"):
                        k = "var_not_equals"
                try:
                    if k in ("exists", "appear", "控件存在"):
                        wait_element(name, timeout=to)
                        return True
                    if k in ("not_exists", "disappear", "控件不存在"):
                        assert_element_not_exists(name, timeout=to)
                        return True
                    if k in ("text_contains", "文本包含"):
                        deadline = time.time() + to
                        target = exp or custom_txt or name
                        last_err = None
                        while time.time() < deadline:
                            try:
                                assert_text_on_screen(target)
                                return True
                            except Exception as e:
                                last_err = e
                                time.sleep(0.4)
                        print(f"branch text_contains miss: {target} ({last_err})")
                        return False
                    if k in ("var_equals", "变量等于", "var_eq", "var_not_equals", "变量不等于", "var_ne"):
                        key = (var_key or name or "").strip()
                        if key.startswith("{{") and key.endswith("}}"):
                            key = key[2:-2].strip()
                        actual = str(_VARS.get(key, ""))
                        want = str(exp or "")
                        ok = (actual == want)
                        if k in ("var_not_equals", "变量不等于", "var_ne"):
                            ok = not ok
                        print(f"branch {k}: {key}={actual!r} vs expected={want!r} -> {ok}")
                        return ok
                    lit = custom_txt.strip().lower()
                    if lit in ("true", "1", "yes"):
                        return True
                    if lit in ("false", "0", "no", ""):
                        return False
                    if custom_txt:
                        try:
                            assert_text_on_screen(custom_txt)
                            return True
                        except Exception:
                            return False
                    return False
                except Exception as e:
                    print(f"branch condition False ({k}/{name}): {e}")
                    return False
            
            
            def _cf_begin_if(cond_ok):
                depth = len(_CF_SKIP)
                while _CF_PENDING and _CF_PENDING[-1].get("depth") == depth:
                    _CF_PENDING.pop()
                parent_skip = _cf_should_skip()
                matched = (not parent_skip) and bool(cond_ok)
                _CF_OPEN.append({"kind": "if", "matched": matched, "depth": depth})
                _CF_SKIP.append(parent_skip or (not matched))
                print(f"IF -> {'THEN' if matched else 'SKIP'} (open={len(_CF_OPEN)})")
            
            
            def _cf_else_if(cond_ok):
                depth = len(_CF_SKIP)
                parent_skip = _cf_should_skip()
                pending = _CF_PENDING[-1] if (_CF_PENDING and _CF_PENDING[-1].get("depth") == depth) else None
                if pending is not None:
                    _CF_PENDING.pop()
                if parent_skip or pending is None:
                    chain_matched = bool(pending.get("matched")) if pending else False
                    skip = True
                    if pending is None:
                        print("WARN: else_if without preceding if block")
                    else:
                        print("ELSE IF -> SKIP (parent skip)")
                elif pending.get("matched"):
                    chain_matched = True
                    skip = True
                    print("ELSE IF -> SKIP (already matched)")
                else:
                    chain_matched = bool(cond_ok)
                    skip = not chain_matched
                    print(f"ELSE IF -> {'THEN' if chain_matched else 'SKIP'}")
                _CF_OPEN.append({"kind": "else_if", "matched": chain_matched, "depth": depth})
                _CF_SKIP.append(skip)
            
            
            def _cf_else():
                depth = len(_CF_SKIP)
                parent_skip = _cf_should_skip()
                pending = _CF_PENDING[-1] if (_CF_PENDING and _CF_PENDING[-1].get("depth") == depth) else None
                if pending is not None:
                    _CF_PENDING.pop()
                if parent_skip or pending is None or pending.get("matched"):
                    skip = True
                    chain_matched = True if (pending and pending.get("matched")) else False
                    print("ELSE -> SKIP")
                else:
                    skip = False
                    chain_matched = True
                    print("ELSE -> THEN")
                _CF_OPEN.append({"kind": "else", "matched": chain_matched, "depth": depth})
                _CF_SKIP.append(skip)
            
            
            def _cf_end():
                if not _CF_OPEN:
                    if _CF_SKIP:
                        _CF_SKIP.pop()
                    print("WARN: end_block without open if/else block")
                    return
                open_b = _CF_OPEN.pop()
                if _CF_SKIP:
                    _CF_SKIP.pop()
                kind = open_b.get("kind") or "if"
                depth = int(open_b.get("depth") or 0)
                if kind == "else":
                    print("END ELSE")
                else:
                    _CF_PENDING.append({"depth": depth, "matched": bool(open_b.get("matched"))})
                    print(f"END {kind} (pending matched={bool(open_b.get('matched'))})")
            
            """;

    public String generate(String visualJson) {
        return generate(visualJson, 1, Map.of());
    }

    private static final Set<String> GENERIC_CLICK_ELEMENTS = Set.of(
            "content", "Root", "root", "android:id/content", "decor_content_parent"
    );

    public String generate(String visualJson, int startFromStep) {
        return generate(visualJson, startFromStep, Map.of());
    }

    public String generate(String visualJson, int startFromStep, Map<String, String> runtimeVars) {
        return generate(visualJson, startFromStep, runtimeVars, null);
    }

    public String generate(String visualJson, int startFromStep, Map<String, String> runtimeVars, String appPackage) {
        try {
            JsonNode root = objectMapper.readTree(visualJson);
            JsonNode rawSteps = root.has("steps") ? root.get("steps") : root;
            Map<String, String> params = runtimeVars != null ? new LinkedHashMap<>(runtimeVars) : Map.of();
            ArrayNode expanded = appendExecutionHelpers(
                    expandSteps(rawSteps, new HashSet<>(), params),
                    root.has("meta") ? root.get("meta") : root);
            boolean humanDelay = root.path("human_delay").asBoolean(false);
            String header = humanDelay
                    ? RUNTIME_HEADER.replace("HUMAN_DELAY = os.environ.get(\"ATP_HUMAN_DELAY\", \"0\") == \"1\"", "HUMAN_DELAY = True")
                    : RUNTIME_HEADER;
            StringBuilder sb = new StringBuilder(header);
            sb.append("\n# === Generated from TestFlow visual editor ===\n");
            if (startFromStep > 1) {
                sb.append("# Checkpoint resume from step ").append(startFromStep).append("\n");
            }
            if (!expanded.isArray() || expanded.isEmpty()) {
                return sb.append("print('No steps defined')\n").toString();
            }
            appendBootstrap(sb, expanded, appPackage);
            int index = 0;
            for (JsonNode step : expanded) {
                index++;
                if (!step.path("enabled").asBoolean(true)) {
                    sb.append("\n# Step ").append(index).append(" SKIPPED: ")
                            .append(step.path("disable_reason").asText("disabled")).append("\n");
                    continue;
                }
                if (index < startFromStep) {
                    sb.append("\n# Step ").append(index).append(" SKIPPED (checkpoint resume)\n");
                    continue;
                }
                String type = step.path("type").asText("wait");
                if (isControlFlowMarker(type, step)) {
                    appendControlFlowMarker(sb, index, step, type);
                    continue;
                }
                boolean bypassSkip = "check_anomaly".equals(type);
                if (!bypassSkip) {
                    // 普通步骤打断同层 if→else if 衔接；未命中分支则跳过
                    sb.append("\n_cf_break_chain()\n");
                    sb.append("if _cf_should_skip():\n");
                    sb.append("    emit_step_begin(").append(index).append(", ").append(q(type)).append(", ")
                            .append(displayLabel(step)).append(")\n");
                    sb.append("    emit_step_end(").append(index).append(", 'skip', 'control-flow skip')\n");
                    sb.append("    print('STEP_SKIPPED:step=").append(index).append(" reason=control-flow')\n");
                    sb.append("else:\n");
                }
                StringBuilder inner = new StringBuilder();
                if ("manual_wait".equals(type)) {
                    appendManualWaitStep(inner, index, step);
                } else if (isAssertStep(type)) {
                    appendAssertStepWithRetry(inner, index, step, type);
                } else {
                    appendStepWithRetry(inner, index, step, type);
                }
                if (bypassSkip) {
                    sb.append(inner);
                } else {
                    indentBlock(sb, inner.toString(), 4);
                }
            }
            sb.append("\nif _CF_OPEN or _CF_SKIP or _CF_PENDING:\n");
            sb.append("    print('WARN: unclosed control-flow open=', len(_CF_OPEN), 'skip=', len(_CF_SKIP), 'pending=', len(_CF_PENDING))\n");
            sb.append("    _CF_OPEN.clear()\n");
            sb.append("    _CF_SKIP.clear()\n");
            sb.append("    _CF_PENDING.clear()\n");
            sb.append("\nprint('Visual case execution finished')\n");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("可视化脚本编译失败: " + e.getMessage(), e);
        }
    }

    private static boolean isControlFlowMarker(String type, JsonNode step) {
        if ("else_if".equals(type) || "else".equals(type) || "elif".equals(type)) {
            return true;
        }
        if ("end_block".equals(type)) {
            // 循环结束块暂不参与 if 栈；分支结束块负责 pop
            String bt = step.path("block_type").asText("branch");
            return !"loop".equals(bt);
        }
        if ("branch".equals(type)) {
            // try_catch 尚未结构化，不进入 if 栈
            return !"try_catch".equals(step.path("branch_mode").asText(""));
        }
        // 兼容：branch + branch_mode=else_if / else
        String mode = step.path("branch_mode").asText("");
        return "else_if".equals(mode) || "elif".equals(mode) || "else".equals(mode);
    }

    private void appendControlFlowMarker(StringBuilder sb, int index, JsonNode step, String type) {
        String mode = step.path("branch_mode").asText("");
        String effective = type;
        if ("branch".equals(type) && ("else_if".equals(mode) || "elif".equals(mode) || "else".equals(mode))) {
            effective = mode;
        }
        if ("elif".equals(effective)) {
            effective = "else_if";
        }

        sb.append("\n# Step ").append(index).append(": ").append(effective).append("\n");
        sb.append("emit_step_begin(").append(index).append(", ").append(q(effective)).append(", ")
                .append(displayLabel(step)).append(")\n");
        sb.append("try:\n");

        if ("end_block".equals(effective)) {
            sb.append("    _cf_end()\n");
        } else if ("else".equals(effective)) {
            sb.append("    _cf_else()\n");
        } else if ("else_if".equals(effective)) {
            sb.append("    _ok = False\n");
            sb.append("    if not _cf_should_skip():\n");
            sb.append("        _pend = _CF_PENDING[-1] if (_CF_PENDING and _CF_PENDING[-1].get('depth') == len(_CF_SKIP)) else None\n");
            sb.append("        if _pend is not None and not _pend.get('matched'):\n");
            sb.append("            _ok = ").append(buildBranchConditionCall(step)).append("\n");
            sb.append("    _cf_else_if(_ok)\n");
        } else {
            // if / branch
            sb.append("    _ok = False if _cf_should_skip() else ").append(buildBranchConditionCall(step)).append("\n");
            sb.append("    _cf_begin_if(_ok)\n");
        }

        sb.append("    emit_step_end(").append(index).append(", 'ok')\n");
        sb.append("except Exception as _cf_e:\n");
        sb.append("    emit_step_end(").append(index).append(", 'fail', str(_cf_e))\n");
        sb.append("    raise\n");
    }

    private String buildBranchConditionCall(JsonNode step) {
        String kind = step.path("condition_kind").asText("");
        if (kind.isBlank()) {
            String cond = step.path("condition").asText("");
            if ("控件不存在".equals(cond)) kind = "not_exists";
            else if ("文本包含".equals(cond)) kind = "text_contains";
            else if ("变量等于".equals(cond) || (cond.startsWith("{{") && cond.contains("=="))) kind = "var_equals";
            else if ("变量不等于".equals(cond)) kind = "var_not_equals";
            else if ("控件存在".equals(cond) || cond.isBlank()) kind = "exists";
            else kind = "custom";
        }
        String element = step.path("element_name").asText("");
        if (element.isBlank()) {
            element = step.path("locator_value").asText("");
        }
        String expected = step.path("expected").asText("");
        int timeout = Math.max(1, step.path("timeout").asInt(step.path("seconds").asInt(5)));
        String custom = step.path("condition").asText("");
        String varName = step.path("var_name").asText("");
        if (varName.isBlank() && ("var_equals".equals(kind) || "var_not_equals".equals(kind))) {
            // 兼容：变量名写在 element_name 里
            varName = element;
        }
        return "eval_branch_condition("
                + q(kind) + ", "
                + "element_name=" + q(element) + ", "
                + "expected=" + q(expected) + ", "
                + "timeout=" + timeout + ", "
                + "custom=" + q(custom) + ", "
                + "var_name=" + q(varName) + ")";
    }

    private static void indentBlock(StringBuilder out, String block, int spaces) {
        String pad = " ".repeat(Math.max(0, spaces));
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                // 保留空行，但避免文件开头多余空行造成语法问题：仍输出换行
                if (i < lines.length - 1 || block.endsWith("\n")) {
                    out.append("\n");
                }
                continue;
            }
            out.append(pad).append(line).append("\n");
        }
    }

    /** 执行期辅助步骤：仅写入脚本，不污染录制审阅步骤列表。 */
    private ArrayNode appendExecutionHelpers(ArrayNode steps, JsonNode meta) {
        if (meta != null && meta.path("skip_execution_helpers").asBoolean(false)) {
            return steps;
        }
        ArrayNode enriched = objectMapper.createArrayNode();
        for (JsonNode step : steps) {
            enriched.add(step);
            if ("launch".equals(step.path("type").asText()) && step.path("enabled").asBoolean(true)) {
                ObjectNode dismiss = objectMapper.createObjectNode();
                dismiss.put("type", "dismiss_popup");
                dismiss.put("enabled", true);
                dismiss.put("_auto_generated", true);
                enriched.add(dismiss);
            }
        }
        if (!enriched.isEmpty() && (meta == null || meta.path("append_anomaly_check").asBoolean(true))) {
            ObjectNode anomaly = objectMapper.createObjectNode();
            anomaly.put("type", "check_anomaly");
            anomaly.put("check_types", "all");
            anomaly.put("enabled", true);
            anomaly.put("_auto_generated", true);
            enriched.add(anomaly);
        }
        return enriched;
    }

    private void appendBootstrap(StringBuilder sb, ArrayNode steps, String appPackage) {
        boolean hasLaunch = false;
        for (JsonNode step : steps) {
            if ("launch".equals(step.path("type").asText())) {
                hasLaunch = true;
                break;
            }
        }
        sb.append("\n# === Bootstrap ===\n");
        if (appPackage != null && !appPackage.isBlank()) {
            if (!hasLaunch) {
                sb.append("launch_app(").append(q(appPackage)).append(")\n");
                sb.append("time.sleep(1.0)\n");
                sb.append("try:\n    warm_ui_cache(serial, blocking=True)\n    from record_helper import wait_page_ready\n    wait_page_ready(serial, timeout=10)\nexcept Exception as _e:\n    print('page ready hook:', _e)\n");
            }
        } else if (!hasLaunch) {
            // 禁止按 HOME：会回到桌面，后续 tap_xy 极易误点 Launcher 图标打开其他 App
            sb.append("print('WARN: 未设置 app_package — 请先在调试页填写包名，或确保设备停在被测应用页面')\n");
        }
    }

    private void appendStepWithRetry(StringBuilder sb, int index, JsonNode step, String type) {
        int retries = Math.max(0, step.path("retry_count").asInt(0));
        String onFail = step.path("on_fail").asText("fail");
        String body = buildStepBody(type, step);
        int attempts = retries + 1;

        sb.append("\n# Step ").append(index).append(": ").append(type);
        if (retries > 0) {
            sb.append(" (max_retry=").append(retries).append(")");
        }
        sb.append("\n");
        sb.append("emit_step_begin(").append(index).append(", ").append(q(type)).append(", ")
                .append(displayLabel(step)).append(")\n");
        sb.append("try:\n");
        sb.append("    for _attempt in range(").append(attempts).append("):\n");
        sb.append("        try:\n");
        appendIndentedBody(sb, body, "            ");
        sb.append("            emit_step_end(").append(index).append(", 'ok')\n");
        sb.append("            break\n");
        sb.append("        except Exception as _step_e:\n");
        sb.append("            if _attempt >= ").append(retries).append(":\n");
        appendOnFailHandler(sb, index, onFail);
        if (retries > 0 && !continuesOnFail(onFail)) {
            sb.append("            human_pause(1)\n");
        }
        sb.append("except Exception:\n");
        sb.append("    raise\n");
    }

    /** 将多行步骤体嵌入固定前缀缩进，保留相对缩进（避免 try/if 体被 strip 成同级）。 */
    private static void appendIndentedBody(StringBuilder sb, String body, String prefix) {
        if (body == null || body.isBlank()) return;
        String[] lines = body.split("\n", -1);
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) continue;
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') i++;
            minIndent = Math.min(minIndent, i);
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            String content = line.length() >= minIndent ? line.substring(minIndent) : line.stripLeading();
            sb.append(prefix).append(content).append("\n");
        }
    }

    /** 失败/异常时的脚本分支：fail 终止；skip/exception/ignore 继续；interrupt 立即中断 */
    private void appendOnFailHandler(StringBuilder sb, int index, String onFail) {
        String policy = normalizeOnFail(onFail);
        switch (policy) {
            case "skip" -> {
                sb.append("                emit_step_end(").append(index).append(", 'skip', str(_step_e))\n");
                sb.append("                print('STEP_SKIPPED:step=").append(index).append(" reason=', _step_e)\n");
                sb.append("                break\n");
            }
            case "ignore" -> {
                sb.append("                emit_step_end(").append(index).append(", 'ignore', str(_step_e))\n");
                sb.append("                print('STEP_IGNORED:step=").append(index).append(" reason=', _step_e)\n");
                sb.append("                break\n");
            }
            case "exception" -> {
                sb.append("                emit_step_end(").append(index).append(", 'exception', str(_step_e))\n");
                sb.append("                print('STEP_EXCEPTION:step=").append(index).append(" reason=', _step_e)\n");
                sb.append("                break\n");
            }
            case "interrupt" -> {
                sb.append("                emit_step_end(").append(index).append(", 'interrupt', str(_step_e))\n");
                sb.append("                print('STEP_INTERRUPT:step=").append(index).append("')\n");
                sb.append("                raise RuntimeError('STEP_INTERRUPT:step=").append(index).append("')\n");
            }
            default -> {
                sb.append("                emit_step_end(").append(index).append(", 'fail', str(_step_e))\n");
                sb.append("                print('CHECKPOINT_FAILED:step=").append(index).append("')\n");
                sb.append("                raise\n");
            }
        }
    }

    private static String normalizeOnFail(String onFail) {
        if (onFail == null || onFail.isBlank() || "restart_app".equals(onFail)) {
            return "fail";
        }
        return onFail;
    }

    private static boolean continuesOnFail(String onFail) {
        String p = normalizeOnFail(onFail);
        return "skip".equals(p) || "ignore".equals(p) || "exception".equals(p);
    }

    private void appendManualWaitStep(StringBuilder sb, int index, JsonNode step) {
        String prompt = step.path("prompt").asText(step.path("expected").asText("请人工处理后继续"));
        sb.append("\n# Step ").append(index).append(": manual_wait\n");
        sb.append("print('ATP_MANUAL_WAIT:step=").append(index).append(":prompt=").append(prompt.replace("'", "\\'")).append("')\n");
        sb.append("raise RuntimeError('MANUAL_WAIT')\n");
    }

    private boolean isAssertStep(String type) {
        return type.startsWith("assert_") || "clipboard_assert".equals(type) || "check_anomaly".equals(type);
    }

    private String assertTarget(JsonNode step, String type) {
        if (step.has("element_name") && !step.path("element_name").asText("").isBlank()) {
            return step.path("element_name").asText("");
        }
        if (step.has("expected")) return step.path("expected").asText("");
        return "";
    }

    private void appendAssertStepWithRetry(StringBuilder sb, int index, JsonNode step, String type) {
        int retries = Math.max(0, step.path("retry_count").asInt(0));
        String onFail = step.path("on_fail").asText("fail");
        String target = assertTarget(step, type);
        String innerBody = buildStepBody(type, step);
        int attempts = retries + 1;

        sb.append("\n# Step ").append(index).append(": ").append(type).append(" (assert)\n");
        sb.append("emit_step_begin(").append(index).append(", ").append(q(type)).append(", ")
                .append(displayLabel(step)).append(")\n");
        sb.append("try:\n");
        sb.append("    for _attempt in range(").append(attempts).append("):\n");
        sb.append("        try:\n");
        sb.append("            def _assert_fn():\n");
        appendIndentedBody(sb, innerBody, "                ");
        sb.append("            _guard_assert(").append(q(type)).append(", ").append(q(target)).append(", _assert_fn)\n");
        sb.append("            emit_step_end(").append(index).append(", 'ok')\n");
        sb.append("            break\n");
        sb.append("        except Exception as _step_e:\n");
        sb.append("            if _attempt >= ").append(retries).append(":\n");
        appendOnFailHandler(sb, index, onFail);
        if (retries > 0 && !continuesOnFail(onFail)) {
            sb.append("            human_pause(1)\n");
        }
        sb.append("except Exception:\n");
        sb.append("    raise\n");
    }

    private ArrayNode expandSteps(JsonNode steps, Set<String> stack, Map<String, String> inheritedParams) {
        ArrayNode result = objectMapper.createArrayNode();
        if (!steps.isArray()) return result;
        for (JsonNode step : steps) {
            String type = step.path("type").asText("");
            if ("invoke_common".equals(type)) {
                String name = step.path("common_step").asText("").trim();
                if (name.isBlank()) {
                    throw new RuntimeException("invoke_common 缺少 common_step 名称");
                }
                if (stack.contains(name)) {
                    throw new RuntimeException("公共步骤循环引用: " + name);
                }
                CommonStep cs = commonStepRepository.findByNameAndDeletedAtIsNull(name)
                        .orElseThrow(() -> new RuntimeException("公共步骤不存在: " + name));
                Map<String, String> params = new LinkedHashMap<>(inheritedParams);
                params.putAll(parseInputParams(step));
                stack.add(name);
                try {
                    JsonNode commonRoot = objectMapper.readTree(cs.getStepsContent());
                    JsonNode innerSteps = commonRoot.has("steps") ? commonRoot.get("steps") : commonRoot;
                    ArrayNode expandedInner = expandSteps(innerSteps, stack, params);
                    for (JsonNode inner : expandedInner) {
                        result.add(applyParamsToStep(inner, params));
                    }
                    appendOutputParams(result, step, cs, params);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("展开公共步骤失败 [" + name + "]: " + e.getMessage(), e);
                } finally {
                    stack.remove(name);
                }
            } else if ("invoke_case".equals(type)) {
                long caseId = step.path("case_id").asLong(0);
                if (caseId <= 0) {
                    throw new RuntimeException("invoke_case 缺少 case_id");
                }
                String stackKey = "case:" + caseId;
                if (stack.contains(stackKey)) {
                    throw new RuntimeException("用例嵌套循环引用: #" + caseId);
                }
                TestCase nested = testCaseRepository.findById(caseId)
                        .orElseThrow(() -> new RuntimeException("被调用例不存在: #" + caseId));
                if (nested.getDeletedAt() != null) {
                    throw new RuntimeException("被调用例已删除: #" + caseId);
                }
                String content = nested.getStepsContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                stack.add(stackKey);
                try {
                    JsonNode caseRoot = objectMapper.readTree(content);
                    JsonNode innerSteps = caseRoot.has("steps") ? caseRoot.get("steps") : caseRoot;
                    ArrayNode expandedInner = expandSteps(innerSteps, stack, inheritedParams);
                    for (JsonNode inner : expandedInner) {
                        result.add(applyParamsToStep(inner, inheritedParams));
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("展开用例失败 [#" + caseId + "]: " + e.getMessage(), e);
                } finally {
                    stack.remove(stackKey);
                }
            } else {
                result.add(applyParamsToStep(step, inheritedParams));
            }
        }
        return result;
    }

    private Map<String, String> parseInputParams(JsonNode step) {
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode ip = step.get("input_params");
        if (ip != null && ip.isObject()) {
            ip.fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText("")));
        }
        return params;
    }

    private void appendOutputParams(ArrayNode result, JsonNode invokeStep, CommonStep cs, Map<String, String> params) {
        JsonNode out = invokeStep.get("output_params");
        if (out == null || !out.isObject() || out.isEmpty()) {
            if (cs.getOutputParams() != null && !cs.getOutputParams().isBlank()) {
                try {
                    out = objectMapper.readTree(cs.getOutputParams());
                } catch (Exception ignored) {
                    return;
                }
            } else {
                return;
            }
        }
        out.fields().forEachRemaining(e -> {
            String targetVar = e.getKey();
            String sourceExpr = substitute(e.getValue().asText(""), params);
            ObjectNode assign = objectMapper.createObjectNode();
            assign.put("type", "set_var");
            assign.put("var_name", targetVar);
            assign.put("var_value", sourceExpr);
            assign.put("enabled", true);
            result.add(assign);
        });
    }

    private JsonNode applyParamsToStep(JsonNode step, Map<String, String> params) {
        if (params.isEmpty()) return step;
        try {
            ObjectNode copy = (ObjectNode) objectMapper.readTree(step.toString());
            substituteInNode(copy, params);
            return copy;
        } catch (Exception e) {
            return step;
        }
    }

    private void substituteInNode(ObjectNode node, Map<String, String> params) {
        node.fields().forEachRemaining(entry -> {
            JsonNode val = entry.getValue();
            if (val.isTextual()) {
                node.put(entry.getKey(), substitute(val.asText(), params));
            } else if (val.isObject()) {
                substituteInNode((ObjectNode) val, params);
            }
        });
    }

    private String substitute(String text, Map<String, String> params) {
        String result = text;
        for (Map.Entry<String, String> e : params.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return result;
    }

    private String buildClickStepBody(JsonNode step) {
        int x = step.has("x") && !step.path("x").isNull() ? step.path("x").asInt() : Integer.MIN_VALUE;
        int y = step.has("y") && !step.path("y").isNull() ? step.path("y").asInt() : Integer.MIN_VALUE;
        boolean hasXY = x != Integer.MIN_VALUE && y != Integer.MIN_VALUE;
        String element = step.path("element_name").asText("");
        String display = step.path("display_name").asText("");
        if (display.isBlank()) display = element;
        if (GENERIC_CLICK_ELEMENTS.contains(element)) {
            return "print('SKIP generic click: " + element + "')";
        }
        if (step.has("locators") && step.get("locators").isObject() && !step.get("locators").isEmpty()) {
            return buildTapRecordedCall(step, display, element, hasXY, x, y);
        }
        if (!element.isBlank()) {
            StringBuilder sb = new StringBuilder("tap_element(").append(q(element));
            if (hasXY) sb.append(", fallback_x=").append(x).append(", fallback_y=").append(y);
            if (!display.isBlank() && !display.equals(element)) {
                sb.append(", display_name=").append(q(display));
            } else if (!display.isBlank()) {
                sb.append(", display_name=").append(q(display));
            }
            sb.append(")");
            return sb.toString();
        }
        if (hasXY) {
            return "tap_xy(" + x + ", " + y + ")";
        }
        return "pass";
    }

    private String buildTapRecordedCall(JsonNode step, String display, String element, boolean hasXY, int x, int y) {
        try {
            String locJson = objectMapper.writeValueAsString(step.get("locators"))
                    .replace("\\", "\\\\").replace("\"", "\\\"");
            String chainJson = "[]";
            if (step.has("locator_chain") && step.get("locator_chain").isArray()) {
                chainJson = objectMapper.writeValueAsString(step.get("locator_chain"))
                        .replace("\\", "\\\\").replace("\"", "\\\"");
            }
            StringBuilder sb = new StringBuilder("tap_recorded(\"")
                    .append(locJson).append("\"");
            if (hasXY) {
                sb.append(", ").append(x).append(", ").append(y);
            } else {
                sb.append(", None, None");
            }
            sb.append(", ").append(q(display.isBlank() ? element : display));
            sb.append(", ").append(q(element));
            sb.append(", \"").append(chainJson).append("\")");
            return sb.toString();
        } catch (Exception e) {
            if (hasXY) {
                return "tap_xy(" + x + ", " + y + ")";
            }
            return "tap_element(" + q(element) + ", display_name=" + q(display) + ")";
        }
    }

    private String buildStepBody(String type, JsonNode step) {
        return switch (type) {
            case "wait" -> {
                String waitMode = step.path("wait_mode").asText("fixed");
                int secs = step.path("seconds").asInt(1);
                String el = step.path("element_name").asText("");
                if ("appear".equals(waitMode) || "wait_appear".equals(waitMode)) {
                    yield "wait_element(" + q(el) + ", timeout=" + Math.max(secs, 1) + ")";
                } else if ("disappear".equals(waitMode) || "wait_gone".equals(waitMode)) {
                    yield "wait_element_gone(" + q(el) + ", timeout=" + Math.max(secs, 1) + ")";
                } else {
                    yield "time.sleep(" + Math.max(secs, 1) + ")";
                }
            }
            case "clear_input" -> "clear_input_field(" + q(step.path("element_name").asText("")) + ")";
            case "click" -> buildClickStepBody(step);
            case "select" -> {
                String el = step.path("element_name").asText("");
                String opt = step.path("option_text").asText(step.path("text").asText(""));
                String display = step.path("display_name").asText("");
                yield "select_dropdown(" + q(el) + ", " + q(opt) + ", " + q(display) + ")";
            }
            case "confirm_dialog" -> {
                String el = step.path("element_name").asText("");
                String opt = step.path("option_text").asText(step.path("text").asText(step.path("display_name").asText("确定")));
                String display = step.path("display_name").asText("");
                yield "confirm_dialog_action(" + q(el) + ", " + q(opt) + ", " + q(display) + ")";
            }
            case "upload" -> {
                String el = step.path("element_name").asText("");
                String fp = step.path("file_path").asText(step.path("text").asText(""));
                String display = step.path("display_name").asText("");
                yield "tap_upload_control(" + q(el) + ", " + q(fp) + ", " + q(display) + ")";
            }
            case "tap_xy" -> "tap_xy(" + step.path("x").asInt() + ", " + step.path("y").asInt() + ")";
            case "input" -> "input_text(" + q(step.path("text").asText("")) + ")";
            case "rich_text" -> "input_rich_text(" + q(step.path("text").asText("")) + ")";
            case "hover" -> {
                String el = step.path("element_name").asText("");
                String display = step.path("display_name").asText("");
                if (!el.isBlank()) {
                    yield "hover_element(" + q(el) + ", " + q(display) + ")";
                }
                int hx = step.path("x").asInt();
                int hy = step.path("y").asInt();
                yield "adb_shell(\"input\", \"swipe\", \"" + hx + "\", \"" + hy + "\", \"" + hx + "\", \"" + hy + "\", \"600\")";
            }
            case "launch" -> {
                String pkg = step.path("app_package").asText("");
                if (pkg.isBlank()) pkg = step.path("text").asText("");
                yield "launch_app(" + q(pkg) + ")\n"
                        + "time.sleep(1)\n"
                        + "try:\n"
                        + "    warm_ui_cache(serial, blocking=True)\n"
                        + "    from record_helper import wait_page_ready\n"
                        + "    wait_page_ready(serial, timeout=10)\n"
                        + "except Exception as _page_hook_err:\n"
                        + "    print('page ready hook:', _page_hook_err)\n"
                        + "check_page_anomaly()";
            }
            case "swipe" -> {
                int x1 = step.has("x1") ? step.path("x1").asInt() : step.path("x").asInt(500);
                int y1 = step.has("y1") ? step.path("y1").asInt() : step.path("y").asInt(800);
                int x2 = step.path("x2").asInt(500);
                int y2 = step.path("y2").asInt(400);
                int dur = step.path("duration_ms").asInt(300);
                yield "human_pause(0.1)\nadb_shell(\"input\", \"swipe\", \"" +
                        x1 + "\", \"" + y1 + "\", \"" + x2 + "\", \"" + y2 + "\", \"" + dur + "\")\nhuman_pause(0.3)";
            }
            case "long_press" -> {
                int x = step.path("x").asInt();
                int y = step.path("y").asInt();
                int dur = step.path("duration_ms").asInt(800);
                yield "adb_shell(\"input\", \"swipe\", \"" + x + "\", \"" + y + "\", \"" + x + "\", \"" + y + "\", \"" + dur + "\")";
            }
            case "clear_cache" -> {
                String mode = step.path("mode").asText("disk");
                yield "clear_app_cache(" + q(mode) + ")";
            }
            case "force_stop" -> "clear_app_cache(\"memory\")";
            case "assert_text" -> "assert_text_on_screen(" + q(step.path("expected").asText("")) + ")";
            case "assert_exists" -> "assert_element_exists(" + q(step.path("element_name").asText("")) + ")";
            case "assert_not_exists" -> "assert_element_not_exists(" + q(step.path("element_name").asText(""))
                    + ", timeout=" + step.path("seconds").asInt(3) + ")";
            case "check_anomaly" -> {
                String types = step.path("check_types").asText("all");
                yield "check_page_anomaly(" + q(types) + ")";
            }
            case "assert_process" -> "assert_app_alive()";
            case "assert_toast" -> "assert_toast(" + q(step.path("expected").asText("")) + ", "
                    + step.path("timeout").asInt(5) + ")";
            case "assert_http" -> "assert_http(" + q(step.path("method").asText("GET")) + ", "
                    + q(step.path("url").asText("")) + ", "
                    + step.path("expected_status").asInt(200) + ", "
                    + q(step.path("body_contains").asText("")) + ")";
            case "assert_analytics" -> "assert_analytics(" + q(step.path("event_name").asText("")) + ", "
                    + q(step.path("props_json").asText("{}")) + ", "
                    + q(step.path("verify_url").asText("")) + ", "
                    + step.path("timeout").asInt(15) + ")";
            case "assert_composite" -> "assert_composite(" + q(step.path("conditions").asText("[]")) + ")";
            case "dismiss_popup" -> "dismiss_popups()";
            case "switch_context" -> "switch_context(" + q(step.path("mode").asText("auto")) + ")";
            case "revoke_permissions" -> "revoke_permissions()";
            case "assert_ocr" -> "assert_ocr_text(" + q(step.path("expected").asText("")) + ")";
            case "tap_ocr" -> "tap_ocr_text(" + q(step.path("expected").asText(step.path("text").asText(""))) + ")";
            case "press_key" -> "press_system_key(" + q(step.path("key").asText(step.path("key_name").asText("back"))) + ")";
            case "clipboard_set" -> "set_clipboard_text(" + q(step.path("text").asText("")) + ")";
            case "clipboard_assert" -> "assert_clipboard_text(" + q(step.path("expected").asText("")) + ")";
            case "wake_screen" -> "wake_device_screen()";
            case "lock_screen" -> "lock_device_screen()";
            case "assert_screen" -> "assert_screen_state(" + q(step.path("expected").asText("on")) + ")";
            case "assert_key" -> "assert_key_response(" + q(step.path("key").asText("back")) + ")";
            case "assert_volume" -> "assert_volume_level(" + step.path("expected").asInt(0) + ", "
                    + q(step.path("stream").asText("music")) + ", "
                    + step.path("tolerance").asInt(1) + ")";
            case "assert_volume_change" -> "assert_volume_changed("
                    + q(step.path("direction").asText(step.path("expected").asText("up"))) + ", "
                    + q(step.path("stream").asText("music")) + ", "
                    + q(step.path("key").asText("")) + ")";
            case "assert_image" -> "assert_image_similar(" + q(step.path("template_path").asText("")) + ", "
                    + step.path("threshold").asDouble(0.85) + ", "
                    + step.path("region_x").asInt(0) + ", " + step.path("region_y").asInt(0) + ", "
                    + step.path("region_w").asInt(0) + ", " + step.path("region_h").asInt(0) + ")";
            case "data_factory" -> buildDataFactoryStep(step);
            case "network_profile" -> "apply_network_profile(" + q(step.path("profile").asText(step.path("mode").asText("2g"))) + ")";
            case "reset_network" -> "reset_network_profile()";
            case "capture_crash" -> "capture_crash_now()";
            case "set_locale" -> "set_device_locale(" + q(step.path("locale").asText(step.path("expected").asText("en_us"))) + ")";
            case "collect_performance" -> "collect_performance_metrics()";
            case "assert_cold_start" -> "measure_cold_start_ms(" + step.path("max_ms").asInt(5000) + ")";
            case "use_account_pool" -> "print('Account pool: server-side acquire before execution')";
            case "set_var" -> {
                String name = step.path("var_name").asText(step.path("name").asText(""));
                String val = step.path("var_value").asText(step.path("value").asText(""));
                if (val.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    yield "set_var(" + q(name) + ", var(" + q(val) + "))";
                }
                yield "set_var(" + q(name) + ", " + q(val) + ")";
            }
            case "set_relative_time" -> {
                int offset = step.path("offset_minutes").asInt(5);
                boolean confirmBtn = step.path("confirm").asBoolean(false);
                yield "set_relative_time(" + offset + ", confirm=" + (confirmBtn ? "True" : "False") + ")";
            }
            case "custom_script" -> buildCustomScriptStep(step);
            case "invoke_common" -> "pass  # expanded at compile time";
            case "invoke_case" -> "pass  # expanded at compile time";
            case "end_block" -> "pass  # control block end marker";
            case "branch", "loop" -> "pass  # control marker (body steps follow)";
            case "else_if", "elif" -> "pass  # else_if marker";
            case "else" -> "pass  # else marker";
            case "screenshot" -> """
                    try:
                        from record_helper import capture_screen
                        capture_screen(serial)
                        print('screenshot saved')
                    except Exception as _ss_err:
                        print('screenshot failed:', _ss_err)
                    """;
            case "rotate_screen" -> {
                String dir = step.path("direction").asText("left");
                String rot = "right".equals(dir) ? "1" : "3";
                yield "adb_shell(\"settings\", \"put\", \"system\", \"user_rotation\", \"" + rot + "\")";
            }
            case "set_auto_rotate" -> "adb_shell(\"settings\", \"put\", \"system\", \"accelerometer_rotation\", \""
                    + (step.path("enabled").asBoolean(false) ? "1" : "0") + "\")";
            case "swipe_from_center" -> {
                String dir = step.path("direction").asText("up");
                int dist = step.path("distance").asInt(400);
                int dur = step.path("duration_ms").asInt(300);
                yield "print('swipe_from_center', " + q(dir) + ", " + dist + ", " + dur + ")\\n"
                        + "adb_shell(\"input\", \"swipe\", \"540\", \"960\", \"540\", str(960 - " + dist + "), \"" + dur + "\")";
            }
            case "uninstall_app" -> "adb_shell(\"uninstall\", " + q(step.path("app_package").asText("")) + ")";
            case "assert_compare" -> {
                String op = step.path("op").asText("eq");
                yield "assert_compare(" + q(step.path("actual").asText("")) + ", " + q(op) + ", "
                        + q(step.path("expected").asText("")) + ")";
            }
            case "assert_element_count" -> "print('assert_element_count', " + q(step.path("element_name").asText(""))
                    + ", " + step.path("expected_count").asInt(1) + ")";
            case "assert_attribute" -> "print('assert_attr', " + q(step.path("element_name").asText("")) + ", "
                    + q(step.path("attr_name").asText("")) + ", " + q(step.path("expected").asText("")) + ")";
            case "get_text" -> "print('get_text', " + q(step.path("element_name").asText("")) + ", ->, "
                    + q(step.path("var_name").asText("TEXT")) + ")";
            case "log_element" -> "print('element:', " + q(step.path("element_name").asText("")) + ")";
            case "drag_element" -> "print('drag_element', " + q(step.path("element_name").asText("")) + ", "
                    + step.path("x2").asInt(500) + ", " + step.path("y2").asInt(800) + ")";
            case "scroll_to_element" -> "print('scroll_to_element', " + q(step.path("element_name").asText("")) + ")";
            case "set_find_strategy" -> "print('find_strategy=', " + q(step.path("strategy").asText("default")) + ")";
            case "switch_handle" -> "print('switch_handle=', " + q(step.path("handle").asText("")) + ")";
            case "random_event" -> "print('random_event count=', " + step.path("event_count").asInt(10) + ")";
            case "set_step_interval" -> "print('step_interval_ms=', " + step.path("interval_ms").asInt(0) + ")";
            case "set_touch_mode" -> "print('touch_mode=', " + q(step.path("touch_mode").asText("default")) + ")";
            case "robot_firmware_upgrade" -> "print('robot firmware:', " + q(step.path("firmware_path").asText("")) + ")";
            case "robot_log_assert" -> "print('robot log assert:', " + q(step.path("expected").asText("")) + ")";
            case "robot_send_command" -> "print('robot cmd:', " + q(step.path("command").asText("")) + ")";
            default -> "print(\"Unknown step type: " + type + "\")";
        };
    }

    private String buildCustomScriptStep(JsonNode step) {
        String lang = step.path("script_lang").asText(step.path("language").asText("python"));
        if (lang == null || lang.isBlank()) lang = "python";
        String code = step.path("script_code").asText(
                step.path("script").asText(step.path("text").asText("")));
        if (code == null || code.isBlank()) {
            return "raise RuntimeError('custom_script: 脚本内容为空')";
        }
        String b64 = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        int timeout = Math.max(5, step.path("script_timeout").asInt(step.path("timeout").asInt(120)));
        return "run_custom_script(" + q(lang) + ", " + q(b64) + ", b64=True, timeout=" + timeout + ")";
    }

    private String buildDataFactoryStep(JsonNode step) {
        long tplId = step.path("template_id").asLong(0);
        if (tplId <= 0) return "raise RuntimeError('data_factory: template_id required')";
        try {
            Map<String, Object> def = dataFactoryService.templateDefForScript(tplId);
            String defJson = objectMapper.writeValueAsString(def).replace("\\", "\\\\").replace("\"", "\\\"");
            return """
                    import urllib.request
                    _df = _json.loads("%s")
                    _url = _subst_tpl(_df.get('url', ''))
                    _body = _subst_tpl(_df.get('body', '') or '')
                    _method = (_df.get('method') or 'POST').upper()
                    _req = urllib.request.Request(_url, data=_body.encode('utf-8') if _body else None, method=_method)
                    _req.add_header('Content-Type', 'application/json')
                    for _hk, _hv in (_df.get('headers') or {}).items():
                        _req.add_header(str(_hk), _subst_tpl(str(_hv)))
                    with urllib.request.urlopen(_req, timeout=30) as _resp:
                        _raw = _resp.read().decode('utf-8', errors='ignore')
                    _resp_obj = _json.loads(_raw) if _raw.strip().startswith('{') else {}
                    _extracted = {}
                    for _ek, _ptr in (_df.get('extract') or {}).items():
                        _node = _resp_obj
                        for _part in str(_ptr).strip('/').split('/'):
                            if isinstance(_node, dict):
                                _node = _node.get(_part, {})
                            else:
                                _node = {}
                        _extracted[_ek] = str(_node) if _node is not None else ''
                        set_var(_ek, _extracted[_ek])
                    print('ATP_FACTORY_OUT:' + _json.dumps({'template_id': %d, 'vars': _extracted}, ensure_ascii=False))
                    """.formatted(defJson, tplId);
        } catch (Exception e) {
            return "raise RuntimeError(" + q("造数模板加载失败: " + e.getMessage()) + ")";
        }
    }

    private String displayLabel(JsonNode step) {
        // 与前端一致：用户填写的 element_name（控件名）优先于录制识别的 display_name
        String label = step.path("element_name").asText("");
        if (label.isBlank()) label = step.path("display_name").asText("");
        if (label.isBlank()) label = step.path("expected").asText("");
        if (label.isBlank()) label = step.path("type").asText("step");
        // 控件 content-desc 常含换行（如「今天\n24」「16:27\n…」），压成单行避免脚本语法错误与报告刷屏
        label = label.replace('\u0000', ' ').replaceAll("[\\r\\n\\t]+", " ").trim();
        if (label.length() > 80) {
            label = label.substring(0, 80);
        }
        return q(label);
    }

    /** 生成合法的 Python 双引号字符串字面量 */
    private String q(String s) {
        if (s == null) s = "";
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + "\"";
    }
}
