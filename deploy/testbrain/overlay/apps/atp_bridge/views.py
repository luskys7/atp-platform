"""
ATP 契约 API：健康检查 + RAG 增强用例生成（标准功能用例 JSON）。
"""
from __future__ import annotations

import json
import re
import traceback
from typing import Any, Dict, List

from django.conf import settings
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods

from apps.knowledge.service import get_knowledgeService_instance
from apps.llm import LLMServiceFactory


def _ok(data: dict, status: int = 200):
    return JsonResponse(data, status=status, json_dumps_params={"ensure_ascii": False})


def _err(message: str, status: int = 500, **extra):
    body = {"success": False, "message": message}
    body.update(extra)
    return JsonResponse(body, status=status, json_dumps_params={"ensure_ascii": False})


@csrf_exempt
@require_http_methods(["GET"])
def health(request):
    milvus_ok = False
    milvus_detail = ""
    rag_ok = False
    try:
        ks = get_knowledgeService_instance()
        # 轻量探测：embedder + vector_store 是否已初始化
        milvus_ok = ks.vector_store is not None
        rag_ok = ks.embedder is not None and milvus_ok
        milvus_detail = f"{settings.VECTOR_DB_CONFIG.get('host')}:{settings.VECTOR_DB_CONFIG.get('port')}"
        # 尝试一次空检索以验证连接（失败不阻断 health=degraded）
        try:
            ks.vector_store.search([0.0] * 1024, top_k=1)
            milvus_ok = True
        except Exception as e:
            milvus_detail = f"{milvus_detail} ({e})"
            milvus_ok = False
            rag_ok = False
    except Exception as e:
        milvus_detail = str(e)
        milvus_ok = False
        rag_ok = False

    llm_provider = getattr(settings, "LLM_PROVIDERS", {}).get("default_provider", "")
    has_qwen = bool(__import__("os").getenv("QWEN_API_KEY", ""))
    has_deepseek = bool(__import__("os").getenv("DEEPSEEK_API_KEY", ""))

    healthy = milvus_ok and (has_qwen or has_deepseek)
    return _ok(
        {
            "success": True,
            "status": "ok" if healthy else "degraded",
            "testbrain": True,
            "milvus": milvus_ok,
            "milvus_detail": milvus_detail,
            "rag_knowledge_base": rag_ok,
            "llm_provider": llm_provider,
            "has_llm_key": has_qwen or has_deepseek,
            "embedding_provider": getattr(settings, "EMBEDDING_PROVIDER", ""),
        }
    )


def _extract_json_array(text: str) -> str:
    text = (text or "").strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    m = re.search(r"\[[\s\S]*\]", text)
    return m.group(0) if m else text


def _priority_from_index(i: int, total: int) -> int:
    if i == 0:
        return 0
    if i == 1:
        return 1
    if i < max(2, total // 2):
        return 2
    return 3


def _to_atp_cases(native_cases: List[Dict[str, Any]], platform: str) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    for i, tc in enumerate(native_cases):
        desc = str(tc.get("description") or tc.get("name") or f"用例{i+1}").strip()
        steps_raw = tc.get("test_steps") or tc.get("steps") or []
        expected_raw = tc.get("expected_results") or []
        if not isinstance(steps_raw, list):
            steps_raw = [str(steps_raw)]
        if not isinstance(expected_raw, list):
            expected_raw = [str(expected_raw)]

        steps = []
        for si, step in enumerate(steps_raw):
            exp = expected_raw[si] if si < len(expected_raw) else (
                expected_raw[-1] if expected_raw else "界面与状态符合预期"
            )
            steps.append({"step": str(step).strip(), "expected": str(exp).strip()})

        pri = tc.get("priority")
        if pri is None:
            pri = _priority_from_index(i, len(native_cases))
        try:
            pri = int(pri)
        except Exception:
            pri = 2
        pri = max(0, min(3, pri))

        name = desc
        if not re.match(r"(?i)^tc-p[0-3]：", name):
            name = f"tc-p{pri}：{desc}"

        case_type = "冒烟" if pri == 0 else str(tc.get("case_type") or "功能")
        out.append(
            {
                "name": name,
                "priority": pri,
                "case_type": case_type,
                "module_name": str(tc.get("module_name") or tc.get("module") or "业务功能"),
                "preconditions": str(
                    tc.get("preconditions")
                    or tc.get("precondition")
                    or "测试环境可用；已具备需求所述账号/权限"
                ),
                "test_data": str(tc.get("test_data") or ""),
                "expected_result": str(
                    tc.get("expected_result")
                    or ("；".join(str(x) for x in expected_raw[:3]) if expected_raw else "业务结果符合需求")
                ),
                "platform": platform or "android",
                "steps": steps,
            }
        )
    return out


def _generate_via_agent(requirement: str, max_cases: int) -> List[Dict[str, Any]]:
    from apps.ai_agents.test_case_generator.generator import TestCaseGeneratorAgent

    provider = getattr(settings, "LLM_PROVIDERS", {}).get("default_provider", "qwen")
    llm = LLMServiceFactory.create(provider=provider)
    ks = get_knowledgeService_instance()
    agent = TestCaseGeneratorAgent(
        llm_service=llm,
        knowledge_service=ks,
        case_design_methods=["等价类", "边界值", "场景法"],
        case_categories=["功能", "异常", "冒烟"],
        case_count=max_cases,
    )
    return agent.generate(requirement, input_type="requirement")


def _generate_via_direct_llm(requirement: str, max_cases: int, platform: str) -> List[Dict[str, Any]]:
    """Agent 不可用时的兜底：RAG 检索 + OpenAI 兼容直调，强制 ATP schema。"""
    import os
    import requests

    ks = get_knowledgeService_instance()
    rag = ""
    try:
        rag = ks.search_relevant_knowledge(requirement[:2000]) or ""
    except Exception:
        rag = ""

    provider = getattr(settings, "LLM_PROVIDERS", {}).get("default_provider", "qwen")
    cfg = getattr(settings, "LLM_PROVIDERS", {}).get(provider, {})
    api_key = os.getenv(f"{provider.upper()}_API_KEY", "") or os.getenv("QWEN_API_KEY", "")
    base = (cfg.get("base_url") or "").rstrip("/")
    model = cfg.get("model") or "qwen-plus"
    if not api_key or not base:
        raise ValueError("LLM API Key / base_url 未配置")

    system = """你是资深测试设计专家。仅输出 JSON 对象，不要 markdown。
格式：{"cases":[{"name":"tc-p0：登录页输入正确账号密码点击登录进入首页","priority":0,"case_type":"冒烟","module_name":"...","preconditions":"...","test_data":"","expected_result":"...","steps":[{"step":"...","expected":"..."}]}]}
约束：
- priority 仅 0/1/2/3；至少 1 条 P0 冒烟；步骤为中文手工步骤（禁止 xpath/自动化口吻）
- name 必须是「tc-px：在什么地方做什么操作得到什么结果」，一眼能看出测什么
- 禁止在 name 中写等价类/边界值/判定表/因果图/正交/场景法等设计方法名称
- 禁止「验证XXX」「测试XXX是否」类空泛标题"""
    user = f"""平台={platform}，最多 {max_cases} 条标准功能测试用例。
===== RAG 知识库片段 =====
{rag or '(无命中，仅依据需求)'}
===== 需求 =====
{requirement}
"""
    chat_url = base if base.endswith("/chat/completions") else f"{base}/chat/completions"
    resp = requests.post(
        chat_url,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={
            "model": model,
            "temperature": 0.1,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        },
        timeout=180,
    )
    resp.raise_for_status()
    content = resp.json()["choices"][0]["message"]["content"]
    raw = content.strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?\s*", "", raw)
        raw = re.sub(r"\s*```$", "", raw)
    m = re.search(r"\{[\s\S]*\}", raw)
    obj = json.loads(m.group(0) if m else raw)
    cases = obj.get("cases") or []
    if not isinstance(cases, list) or not cases:
        raise ValueError("LLM 未返回 cases")
    # 若已是 ATP 结构则直接返回（仍走统一规范化）
    if cases and isinstance(cases[0], dict) and "steps" in cases[0]:
        return [
            {
                "description": c.get("name") or c.get("description") or f"用例{i+1}",
                "test_steps": [s.get("step", "") for s in (c.get("steps") or [])],
                "expected_results": [s.get("expected", "") for s in (c.get("steps") or [])],
                "priority": c.get("priority"),
                "module_name": c.get("module_name"),
                "preconditions": c.get("preconditions"),
                "test_data": c.get("test_data"),
                "expected_result": c.get("expected_result"),
                "case_type": c.get("case_type"),
            }
            for i, c in enumerate(cases)
        ]
    return cases


@csrf_exempt
@require_http_methods(["POST"])
def knowledge_ingest(request):
    try:
        body = json.loads(request.body.decode("utf-8") or "{}")
    except Exception:
        return _err("请求体必须是 JSON", 400)
    title = (body.get("title") or "").strip()
    content = (body.get("content") or "").strip()
    if not title or not content:
        return _err("title 与 content 不能为空", 400)
    try:
        ks = get_knowledgeService_instance()
        if ks.embedder is None or ks.vector_store is None:
            return _err("知识库未就绪（Milvus/Embedding）", 503)
        kid = ks.add_knowledge(title, content)
        return _ok({"success": True, "knowledge_id": kid, "message": "已入库"})
    except Exception as e:
        return _err(f"入库失败: {e}", 502)


@csrf_exempt
@require_http_methods(["GET"])
def knowledge_list(request):
    try:
        from apps.core.models import KnowledgeBase

        items = []
        for item in KnowledgeBase.objects.all().order_by("-created_at")[:100]:
            items.append(
                {
                    "id": item.id,
                    "title": item.title,
                    "content": (item.content or "")[:500],
                    "created_at": item.created_at.isoformat()
                    if getattr(item, "created_at", None)
                    else "",
                }
            )
        return _ok({"success": True, "knowledge_items": items, "count": len(items)})
    except Exception as e:
        return _err(f"列表失败: {e}", 502, knowledge_items=[], count=0)


@csrf_exempt
@require_http_methods(["POST"])
def generate_cases(request):
    try:
        body = json.loads(request.body.decode("utf-8") or "{}")
    except Exception:
        return _err("请求体必须是 JSON", 400)

    requirement = (body.get("requirement") or body.get("prd_text") or "").strip()
    if not requirement:
        return _err("requirement 不能为空", 400)

    platform = (body.get("platform") or "android").strip()
    try:
        max_cases = int(body.get("max_cases") or 8)
    except Exception:
        max_cases = 8
    max_cases = max(1, min(max_cases, 20))

    note = ""
    native: List[Dict[str, Any]] = []
    try:
        native = _generate_via_agent(requirement, max_cases)
        note = "testbrain_agent+rag"
    except Exception as e1:
        try:
            native = _generate_via_direct_llm(requirement, max_cases, platform)
            note = f"direct_llm+rag (agent_fallback: {e1})"
        except Exception as e2:
            return _err(
                f"生成失败: agent={e1}; direct={e2}",
                502,
                trace=traceback.format_exc()[-2000:],
            )

    cases = _to_atp_cases(native, platform)[:max_cases]
    if not cases:
        return _err("未生成到有效用例", 502)

    # 检索命中摘要（便于 ATP status/调试）
    rag_hits = 0
    try:
        ctx = get_knowledgeService_instance().search_relevant_knowledge(requirement[:1500])
        rag_hits = 1 if ctx else 0
    except Exception:
        rag_hits = 0

    return _ok(
        {
            "provider": "testbrain",
            "note": note,
            "schema": body.get("schema") or "atp_standard_case_v1",
            "rag_hits": rag_hits,
            "cases": cases,
        }
    )
