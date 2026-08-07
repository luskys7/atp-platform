from apps.core.models import TestCase, TestCaseReview
from django.contrib.auth.models import User
from django.core.paginator import Paginator, EmptyPage, PageNotAnInteger
from apps.utils.logger_manager import get_logger
import json
from django.shortcuts import render
from apps.ai_agents.test_case_reviewer.reviewer import TestCaseReviewerAgent
from django.http import JsonResponse
import xlwt
from django.views.decorators.http import require_http_methods
from django.views.decorators.csrf import csrf_exempt
from datetime import datetime
from django.http import HttpResponse
from apps.llm import LLMServiceFactory
from apps.knowledge.service import get_knowledgeService_instance
from apps.llm.utils import get_agent_llm_configs
from django.utils.dateparse import parse_date


logger = get_logger(__name__)

DEFAULT_PROVIDER, PROVIDERS = get_agent_llm_configs("test_case_reviewer")
DEFAULT_LLM_CONFIG = PROVIDERS.get(DEFAULT_PROVIDER, {})

llm_service = LLMServiceFactory.create(
    provider=DEFAULT_PROVIDER,
)

knowledge_service = get_knowledgeService_instance()


def _parse_filters(request):
    project = (request.GET.get("project") or "").strip()
    keyword = (request.GET.get("q") or "").strip()
    date_from = (request.GET.get("date_from") or "").strip()
    date_to = (request.GET.get("date_to") or "").strip()
    tab = (request.GET.get("tab") or "pending").strip().lower()
    if tab not in ("pending", "approved", "rejected"):
        tab = "pending"
    valid = {k for k, _ in TestCase.BU_CHOICES}
    if project and project not in valid:
        project = ""
    return project, keyword, date_from, date_to, tab


def _apply_filters(qs, project, keyword, date_from, date_to):
    if project:
        qs = qs.filter(bu=project)
    if keyword:
        qs = qs.filter(description__icontains=keyword)
    df = parse_date(date_from) if date_from else None
    dt = parse_date(date_to) if date_to else None
    if df:
        qs = qs.filter(created_at__date__gte=df)
    if dt:
        qs = qs.filter(created_at__date__lte=dt)
    return qs


def _paginate(qs, page_key, request, page_size=15):
    paginator = Paginator(qs, page_size)
    page = request.GET.get(page_key, 1)
    try:
        return paginator.page(page)
    except PageNotAnInteger:
        return paginator.page(1)
    except EmptyPage:
        return paginator.page(paginator.num_pages)


def _query_string(project, keyword, date_from, date_to, tab=None, **extra):
    from urllib.parse import urlencode

    params = {}
    if project:
        params["project"] = project
    if keyword:
        params["q"] = keyword
    if date_from:
        params["date_from"] = date_from
    if date_to:
        params["date_to"] = date_to
    if tab:
        params["tab"] = tab
    params.update({k: v for k, v in extra.items() if v is not None and v != ""})
    return urlencode(params)


def review_view(request):
    """测试用例评审页：筛选 + Tab + 分页。"""
    project, keyword, date_from, date_to, tab = _parse_filters(request)
    base_qs = _apply_filters(TestCase.objects.all(), project, keyword, date_from, date_to)

    pending_qs = base_qs.filter(status="pending").order_by("-created_at")
    approved_qs = base_qs.filter(status="approved").order_by("-created_at")
    rejected_qs = base_qs.filter(status="rejected").order_by("-created_at")

    pending_test_cases = _paginate(pending_qs, "pending_page", request)
    approved_test_cases = _paginate(approved_qs, "approved_page", request)
    rejected_test_cases = _paginate(rejected_qs, "rejected_page", request)

    qs_filters = _query_string(project, keyword, date_from, date_to, tab="")

    context = {
        "pending_test_cases": pending_test_cases,
        "approved_test_cases": approved_test_cases,
        "rejected_test_cases": rejected_test_cases,
        "pending_total": pending_qs.count(),
        "approved_total": approved_qs.count(),
        "rejected_total": rejected_qs.count(),
        "active_tab": tab,
        "current_project": project,
        "keyword": keyword,
        "date_from": date_from,
        "date_to": date_to,
        "projects": list(TestCase.BU_CHOICES),
        "query_base": qs_filters,
    }
    return render(request, "review.html", context)


@csrf_exempt
@require_http_methods(["POST"])
def case_review(request):
    """AI 评审 API。"""
    try:
        data = json.loads(request.body)
        test_case_id = data.get("test_case_id")
        if not test_case_id:
            return JsonResponse({"success": False, "message": "测试用例ID不能为空"}, status=400)
        try:
            test_case = TestCase.objects.get(id=test_case_id)
        except TestCase.DoesNotExist:
            return JsonResponse({"success": False, "message": f"找不到ID为 {test_case_id} 的测试用例"}, status=404)

        test_case_reviewer = TestCaseReviewerAgent(llm_service, knowledge_service)
        review_result = test_case_reviewer.review(test_case)
        review_content = review_result.content if hasattr(review_result, "content") else str(review_result)

        return JsonResponse({"success": True, "review_result": review_content})
    except json.JSONDecodeError:
        return JsonResponse({"success": False, "message": "无效的JSON数据"}, status=400)
    except Exception as e:
        logger.error(f"评审过程中出错: {str(e)}", exc_info=True)
        return JsonResponse({"success": False, "message": f"评审失败：{str(e)}"}, status=500)


def case_review_detail(request):
    return render(request, "case_review_detail.html")


@require_http_methods(["GET"])
def get_test_case(request, test_case_id):
    """单个测试用例详情（含历史评审）。"""
    try:
        test_case = TestCase.objects.get(id=test_case_id)
        reviews = []
        for rv in test_case.reviews.all().order_by("-review_date"):
            reviews.append(
                {
                    "id": rv.id,
                    "comments": rv.review_comments,
                    "review_date": rv.review_date.strftime("%Y-%m-%d %H:%M:%S") if rv.review_date else "",
                    "reviewer": getattr(rv.reviewer, "username", "") if rv.reviewer_id else "",
                }
            )
        return JsonResponse(
            {
                "success": True,
                "id": test_case.id,
                "title": test_case.title,
                "description": test_case.description,
                "test_steps": test_case.test_steps,
                "expected_results": test_case.expected_results,
                "status": test_case.status,
                "bu": test_case.bu,
                "created_at": test_case.created_at.strftime("%Y-%m-%d %H:%M:%S") if test_case.created_at else "",
                "updated_at": test_case.updated_at.strftime("%Y-%m-%d %H:%M:%S") if test_case.updated_at else "",
                "reviews": reviews,
            }
        )
    except TestCase.DoesNotExist:
        return JsonResponse({"success": False, "error": "测试用例不存在"}, status=404)


def get_test_cases(request, test_case_ids: str):
    try:
        ids = test_case_ids.split(",")
        test_cases = TestCase.objects.filter(id__in=ids).values(
            "id",
            "title",
            "description",
            "test_steps",
            "expected_results",
            "status",
            "requirements",
            "llm_provider",
            "created_at",
        )
        rows = []
        for tc in test_cases:
            item = dict(tc)
            if item.get("created_at"):
                item["created_at"] = item["created_at"].strftime("%Y-%m-%d %H:%M:%S")
            rows.append(item)
        return JsonResponse({"success": True, "test_cases": rows})
    except Exception as e:
        return JsonResponse({"success": False, "message": str(e)}, status=500)


@csrf_exempt
@require_http_methods(["POST"])
def update_test_case(request):
    data = json.loads(request.body)
    try:
        test_case = TestCase.objects.get(id=data["test_case_id"])
        if "status" in data:
            test_case.status = data["status"]
        if "description" in data:
            test_case.description = data["description"]
        if "test_steps" in data:
            test_case.test_steps = data["test_steps"]
        if "expected_results" in data:
            test_case.expected_results = data["expected_results"]
        test_case.save()
        return JsonResponse({"success": True})
    except TestCase.DoesNotExist:
        return JsonResponse({"success": False, "message": "测试用例不存在"})
    except Exception as e:
        return JsonResponse({"success": False, "message": str(e)})


def _get_or_create_system_user():
    user, _ = User.objects.get_or_create(
        username="system",
        defaults={"is_active": True},
    )
    return user


@csrf_exempt
@require_http_methods(["POST"])
def manual_review(request):
    """单条人工评审：通过 / 驳回，必填意见。"""
    try:
        data = json.loads(request.body)
        test_case_id = data.get("test_case_id")
        status = (data.get("status") or "").strip()
        comments = (data.get("comments") or "").strip()
        if not test_case_id:
            return JsonResponse({"success": False, "message": "缺少用例 ID"})
        if status not in ("approved", "rejected"):
            return JsonResponse({"success": False, "message": "状态只能是 approved / rejected"})
        if not comments:
            return JsonResponse({"success": False, "message": "评审意见不能为空"})

        test_case = TestCase.objects.get(id=test_case_id)
        if test_case.status != "pending":
            return JsonResponse({"success": False, "message": "仅待评审用例可执行评审操作"})

        test_case.status = status
        test_case.save(update_fields=["status", "updated_at"])
        TestCaseReview.objects.create(
            test_case=test_case,
            reviewer=_get_or_create_system_user(),
            review_comments=comments,
        )
        return JsonResponse({"success": True, "message": "评审成功"})
    except TestCase.DoesNotExist:
        return JsonResponse({"success": False, "message": "测试用例不存在"}, status=404)
    except Exception as e:
        logger.error(f"人工评审失败: {e}", exc_info=True)
        return JsonResponse({"success": False, "message": str(e)}, status=500)


@csrf_exempt
@require_http_methods(["POST"])
def batch_manual_review(request):
    """批量人工评审（仅待评审）。"""
    try:
        data = json.loads(request.body)
        ids = data.get("ids") or []
        status = (data.get("status") or "").strip()
        comments = (data.get("comments") or "").strip()
        if not ids:
            return JsonResponse({"success": False, "message": "请先选择用例"})
        if status not in ("approved", "rejected"):
            return JsonResponse({"success": False, "message": "状态只能是 approved / rejected"})
        if not comments:
            return JsonResponse({"success": False, "message": "批量评审意见不能为空"})

        qs = TestCase.objects.filter(id__in=ids, status="pending")
        reviewer = _get_or_create_system_user()
        updated = 0
        for tc in qs:
            tc.status = status
            tc.save(update_fields=["status", "updated_at"])
            TestCaseReview.objects.create(
                test_case=tc,
                reviewer=reviewer,
                review_comments=comments,
            )
            updated += 1

        skipped = len(ids) - updated
        msg = f"成功处理 {updated} 条"
        if skipped:
            msg += f"，跳过 {skipped} 条非待评审用例"
        return JsonResponse({"success": True, "message": msg, "updated": updated, "skipped": skipped})
    except Exception as e:
        logger.error(f"批量评审失败: {e}", exc_info=True)
        return JsonResponse({"success": False, "message": str(e)}, status=500)


def copy_test_cases(request):
    try:
        ids = request.GET.get("ids")
        response = get_test_cases(request, ids)
        response_data = json.loads(response.content)
        if response_data.get("success"):
            return JsonResponse({"success": True, "test_cases": response_data.get("test_cases")})
        return JsonResponse({"success": False, "message": response_data.get("message")})
    except Exception as e:
        return JsonResponse({"success": False, "message": str(e)}, status=500)


def export_test_cases_excel(request):
    try:
        ids = request.GET.get("ids")
        if not ids:
            return JsonResponse({"success": False, "message": "未提供测试用例ID"})

        response = get_test_cases(request, ids)
        response_data = json.loads(response.content)
        if not response_data.get("success"):
            return JsonResponse({"success": False, "message": "获取测试用例数据失败"})

        test_cases = response_data.get("test_cases")
        wb = xlwt.Workbook(encoding="utf-8")
        ws = wb.add_sheet("测试用例")
        header_style = xlwt.XFStyle()
        header_font = xlwt.Font()
        header_font.bold = True
        header_style.font = header_font

        headers = ["序号", "用例描述", "测试步骤", "预期结果", "状态", "创建时间"]
        for col, header in enumerate(headers):
            ws.write(0, col, header, header_style)
            ws.col(col).width = 256 * 30

        status_map = {"pending": "待评审", "approved": "已通过", "rejected": "未通过"}
        for row, test_case in enumerate(test_cases, start=1):
            ws.write(row, 0, row)
            ws.write(row, 1, test_case.get("description", ""))
            ws.write(row, 2, test_case.get("test_steps", ""))
            ws.write(row, 3, test_case.get("expected_results", ""))
            ws.write(row, 4, status_map.get(test_case.get("status", ""), test_case.get("status", "")))
            ws.write(row, 5, test_case.get("created_at", ""))
            ws.row(row).height_mismatch = True
            ws.row(row).height = 20 * 40

        current_time = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"test_cases_{current_time}_{len(test_cases)}_cases.xls"
        resp = HttpResponse(content_type="application/vnd.ms-excel")
        resp["Content-Disposition"] = f'attachment; filename="{filename}"'
        wb.save(resp)
        return resp
    except Exception as e:
        logger.error(f"导出Excel失败: {str(e)}", exc_info=True)
        return JsonResponse({"success": False, "message": f"导出Excel失败: {str(e)}"})


@csrf_exempt
@require_http_methods(["DELETE", "POST"])
def delete_test_cases(request):
    try:
        if request.method == "POST":
            try:
                body = json.loads(request.body or "{}")
                ids = body.get("ids") or request.GET.get("ids", "")
                if isinstance(ids, list):
                    ids = ",".join(str(x) for x in ids)
            except json.JSONDecodeError:
                ids = request.GET.get("ids", "")
        else:
            ids = request.GET.get("ids", "")
        if not ids:
            return JsonResponse({"success": False, "message": "未提供测试用例ID"})
        test_case_ids = [x for x in str(ids).split(",") if x]
        deleted, _ = TestCase.objects.filter(id__in=test_case_ids).delete()
        return JsonResponse({"success": True, "message": f"成功删除选中测试用例"})
    except Exception as e:
        logger.error(f"删除测试用例失败: {str(e)}", exc_info=True)
        return JsonResponse({"success": False, "message": f"删除失败: {str(e)}"})
