import json
import os
from django.conf import settings
from django.http import JsonResponse, HttpResponse
from django.shortcuts import render
from django.views.decorators.http import require_http_methods
from apps.llm import LLMServiceFactory
from apps.ai_agents.test_case_generator.generator import TestCaseGeneratorAgent
from apps.core.models import TestCase, KnowledgeBase
from apps.utils.logger_manager import get_logger
from apps.knowledge.service import get_knowledgeService_instance
from apps.llm.utils import get_agent_llm_configs

try:
    import xlwt
except ImportError:
    xlwt = None

logger = get_logger(__name__)
DEFAULT_PROVIDER, PROVIDERS = get_agent_llm_configs("test_case_generator")
knowledge_service = get_knowledgeService_instance()

TEMPLATE_HINTS = {
    "functional_ui": (
        "用例生成模板：功能测试用例（移动端 UI 自动化）。"
        "步骤需可映射到 App 控件操作（点击/输入/滑动/断言可见文案）。"
    ),
    "api": (
        "用例生成模板：接口测试用例。"
        "步骤需包含请求方法、路径、关键参数/Body、鉴权；预期需包含状态码与关键响应字段。"
    ),
    "boundary": (
        "用例生成模板：边界 & 异常场景用例。"
        "优先覆盖边界值、非法输入、超时、权限不足、空数据等负面路径。"
    ),
}


def _project_dir(project: str) -> str:
    return os.path.join(settings.MEDIA_ROOT, project)


def _list_project_files(project: str):
    docs = []
    root = _project_dir(project)
    if not project or not os.path.isdir(root):
        return docs
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if not os.path.isfile(path):
            continue
        docs.append(
            {
                "id": f"file:{project}/{name}",
                "title": name,
                "source": path,
                "size": os.path.getsize(path),
                "kind": "file",
            }
        )
    return docs


def _load_doc_texts(doc_ids):
    """根据文档 id 读取文本内容（截断，避免 prompt 过大）。"""
    chunks = []
    for doc_id in doc_ids or []:
        text = ""
        title = doc_id
        if isinstance(doc_id, str) and doc_id.startswith("file:"):
            rel = doc_id[5:]
            path = os.path.join(settings.MEDIA_ROOT, rel.replace("/", os.sep))
            title = os.path.basename(path)
            if os.path.isfile(path):
                try:
                    # 优先读纯文本；二进制文档尝试用已入库 KnowledgeBase 摘要
                    ext = os.path.splitext(path)[1].lower()
                    if ext in (".txt", ".md"):
                        with open(path, "r", encoding="utf-8", errors="ignore") as f:
                            text = f.read(8000)
                    else:
                        kb = KnowledgeBase.objects.filter(title__icontains=os.path.splitext(title)[0]).order_by("-created_at").first()
                        if kb:
                            text = kb.content[:8000]
                        else:
                            text = f"（已关联文档 {title}，请基于该文档业务规则生成用例）"
                except Exception as e:
                    text = f"（读取文档失败：{e}）"
        elif isinstance(doc_id, str) and doc_id.startswith("kb:"):
            try:
                kb_id = int(doc_id.split(":", 1)[1])
                kb = KnowledgeBase.objects.get(id=kb_id)
                title = kb.title
                text = (kb.content or "")[:8000]
            except Exception:
                continue
        if text:
            chunks.append(f"### 文档：{title}\n{text}")
    return "\n\n".join(chunks)


async def generate(request):
    logger.info("===== 进入generate视图函数 =====")
    context = {
        "llm_providers": PROVIDERS,
        "llm_provider": DEFAULT_PROVIDER,
        "projects": list(TestCase.BU_CHOICES),
        "requirement": "",
        "test_cases": None,
    }

    if request.method == "GET":
        return render(request, "generate.html", context)

    try:
        data = json.loads(request.body)
    except json.JSONDecodeError:
        return JsonResponse({"success": False, "message": "无效的JSON数据"}, status=400)

    project = (data.get("project") or "").strip()
    valid = {k for k, _ in TestCase.BU_CHOICES}
    if not project or project not in valid:
        return JsonResponse({"success": False, "message": "请先选择项目归属"}, status=400)

    requirements = (data.get("requirements") or "").strip()
    doc_ids = data.get("doc_ids") or []
    doc_context = _load_doc_texts(doc_ids)
    if not requirements and not doc_context:
        return JsonResponse({"success": False, "message": "请输入需求描述或关联知识库文档"}, status=400)

    llm_provider = data.get("llm_provider", DEFAULT_PROVIDER)
    case_design_methods = data.get("case_design_methods") or ["等价类划分法", "场景法"]
    case_categories = data.get("case_categories") or ["功能测试"]
    case_count = int(data.get("case_count") or 5)
    case_template = data.get("case_template") or "functional_ui"
    temperature = float(data.get("temperature") or 0.2)
    include_negative = bool(data.get("include_negative", True))
    include_pre_post = bool(data.get("include_pre_post", True))

    # 组装增强需求
    extra_bits = [TEMPLATE_HINTS.get(case_template, TEMPLATE_HINTS["functional_ui"])]
    if include_negative:
        extra_bits.append("请至少覆盖部分负面/异常场景用例。")
    if include_pre_post:
        extra_bits.append("每条用例必须包含 preconditions（前置）；步骤中如涉及清理请写明后置操作。")
    else:
        extra_bits.append("前置条件可写「无」，无需刻意补充后置步骤。")
    if doc_context:
        extra_bits.append("以下为关联知识库文档内容，请优先贴合文档业务规则生成：\n" + doc_context)

    full_requirements = (requirements + "\n\n" if requirements else "") + "\n".join(extra_bits)

    try:
        llm_kwargs = dict(PROVIDERS.get(llm_provider, {}) or {})
        llm_kwargs["temperature"] = temperature
        try:
            llm_service = LLMServiceFactory.create(llm_provider, **llm_kwargs)
        except TypeError:
            llm_service = LLMServiceFactory.create(llm_provider)

        generator_agent = TestCaseGeneratorAgent(
            llm_service=llm_service,
            knowledge_service=knowledge_service,
            case_design_methods=case_design_methods,
            case_categories=case_categories,
            case_count=case_count,
        )
        # 若已显式关联文档，则优先用文档上下文，减少无关检索噪声
        if doc_context:
            generator_agent._forced_knowledge = (
                "请严格参考以下已选知识库文档内容生成用例：\n" + doc_context
            )

        test_cases = await generator_agent.async_generate(full_requirements, input_type="requirement")
        return JsonResponse({"success": True, "test_cases": test_cases, "project": project})
    except Exception as e:
        logger.error(f"生成测试用例时出错: {str(e)}", exc_info=True)
        return JsonResponse({"success": False, "message": str(e)}, status=500)


@require_http_methods(["GET"])
def list_knowledge_docs(request):
    """按项目列出可关联的知识库文档。"""
    project = (request.GET.get("project") or "").strip()
    valid = {k for k, _ in TestCase.BU_CHOICES}
    if not project or project not in valid:
        return JsonResponse({"success": True, "docs": []})

    docs = _list_project_files(project)
    # 补充 KnowledgeBase（标题/内容含项目标记）
    for kb in KnowledgeBase.objects.filter(content__contains=f"[项目:{project}]").order_by("-created_at")[:50]:
        docs.append(
            {
                "id": f"kb:{kb.id}",
                "title": kb.title,
                "source": "",
                "size": len(kb.content or ""),
                "kind": "kb",
            }
        )
    # 去重标题
    seen = set()
    uniq = []
    for d in docs:
        key = d["title"]
        if key in seen:
            continue
        seen.add(key)
        uniq.append(d)
    return JsonResponse({"success": True, "docs": uniq})


@require_http_methods(["POST"])
def save_test_case(request):
    """保存测试用例（提交至评审）。"""
    try:
        data = json.loads(request.body)
        requirement = data.get("requirement") or ""
        test_cases_list = data.get("test_cases", [])
        llm_provider = data.get("llm_provider")
        project = (data.get("project") or "").strip()

        if not test_cases_list:
            return JsonResponse({"success": False, "message": "测试用例数据为空"}, status=400)

        valid = {k for k, _ in TestCase.BU_CHOICES}
        if project and project not in valid:
            project = ""

        created_ids = []
        for index, test_case in enumerate(test_cases_list, 1):
            steps = test_case.get("test_steps", [])
            expected = test_case.get("expected_results", [])
            if isinstance(steps, str):
                steps_text = steps
            else:
                steps_text = "\n".join(steps or [])
            if isinstance(expected, str):
                expected_text = expected
            else:
                expected_text = "\n".join(expected or [])

            pre = test_case.get("preconditions") or "无"
            if pre and pre != "无":
                steps_text = f"【前置条件】{pre}\n{steps_text}"

            obj = TestCase.objects.create(
                title=f"测试用例-{index}",
                description=test_case.get("description", ""),
                test_steps=steps_text,
                expected_results=expected_text,
                requirements=requirement,
                llm_provider=llm_provider,
                status="pending",
                bu=project or "",
            )
            created_ids.append(obj.id)

        return JsonResponse(
            {
                "success": True,
                "message": f"成功提交 {len(created_ids)} 条测试用例至评审",
                "test_case_id": created_ids,
                "project": project,
            }
        )
    except json.JSONDecodeError:
        return JsonResponse({"success": False, "message": "无效的JSON数据"}, status=400)
    except Exception as e:
        logger.error(f"保存测试用例时出错: {str(e)}", exc_info=True)
        return JsonResponse({"success": False, "message": f"保存失败：{str(e)}"}, status=500)


@require_http_methods(["POST"])
def export_generated_excel(request):
    """导出生成结果为 Excel。"""
    if xlwt is None:
        return JsonResponse({"success": False, "message": "服务端未安装 xlwt"}, status=500)
    try:
        data = json.loads(request.body)
        test_cases = data.get("test_cases") or []
        if not test_cases:
            return JsonResponse({"success": False, "message": "没有可导出的用例"}, status=400)

        wb = xlwt.Workbook(encoding="utf-8")
        ws = wb.add_sheet("生成用例")
        headers = ["序号", "用例标题", "前置条件", "测试步骤", "预期结果"]
        for c, h in enumerate(headers):
            ws.write(0, c, h)
            ws.col(c).width = 256 * 40
        for i, tc in enumerate(test_cases, start=1):
            steps = tc.get("test_steps", [])
            expected = tc.get("expected_results", [])
            steps_text = steps if isinstance(steps, str) else "\n".join(steps or [])
            expected_text = expected if isinstance(expected, str) else "\n".join(expected or [])
            ws.write(i, 0, i)
            ws.write(i, 1, tc.get("description", ""))
            ws.write(i, 2, tc.get("preconditions", "无"))
            ws.write(i, 3, steps_text)
            ws.write(i, 4, expected_text)

        resp = HttpResponse(content_type="application/vnd.ms-excel")
        resp["Content-Disposition"] = 'attachment; filename="generated_cases.xls"'
        wb.save(resp)
        return resp
    except Exception as e:
        return JsonResponse({"success": False, "message": str(e)}, status=500)
