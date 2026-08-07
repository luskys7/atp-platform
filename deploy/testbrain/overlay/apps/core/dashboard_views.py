"""首页仪表盘：项目（BU）维度统计、最近动态。"""
from datetime import datetime

from django.http import JsonResponse
from django.shortcuts import render
from django.views.decorators.http import require_GET

from .models import KnowledgeBase, TestCase, TestCaseReview


def _project_choices():
    return list(TestCase.BU_CHOICES)


def _qs_for_project(project):
    qs = TestCase.objects.all()
    if project:
        qs = qs.filter(bu=project)
    return qs


def _build_stats(project=""):
    qs = _qs_for_project(project)
    return {
        "total_test_cases": qs.count(),
        "pending_count": qs.filter(status="pending").count(),
        "approved_count": qs.filter(status="approved").count(),
        "rejected_count": qs.filter(status="rejected").count(),
    }


def _build_activities(project="", limit=12):
    """合并生成 / 评审 / 上传记录。"""
    items = []
    case_qs = _qs_for_project(project).order_by("-created_at")[:limit]
    for tc in case_qs:
        title = (tc.description or tc.title or "未命名用例")[:80]
        items.append(
            {
                "kind": "generate",
                "kind_label": "AI 生成用例",
                "time": tc.created_at,
                "content": f"生成用例：{title}",
                "detail_url": f"/test_case_reviewer/case-review-detail/?id={tc.id}",
                "fallback_url": "/test_case_reviewer/?tab=pending",
            }
        )

    review_qs = TestCaseReview.objects.select_related("test_case").order_by("-review_date")
    if project:
        review_qs = review_qs.filter(test_case__bu=project)
    for rv in review_qs[:limit]:
        tc = rv.test_case
        title = (tc.description or tc.title or "未命名用例")[:60]
        status_label = dict(TestCase.STATUS_CHOICES).get(tc.status, tc.status)
        items.append(
            {
                "kind": "review",
                "kind_label": "用例评审",
                "time": rv.review_date,
                "content": f"评审「{title}」→ {status_label}",
                "detail_url": f"/test_case_reviewer/case-review-detail/?id={tc.id}",
                "fallback_url": f"/test_case_reviewer/?tab={tc.status}",
            }
        )

    # 知识库条目作为文档上传记录
    for kb in KnowledgeBase.objects.order_by("-created_at")[:limit]:
        items.append(
            {
                "kind": "upload",
                "kind_label": "文档上传",
                "time": kb.created_at,
                "content": f"上传知识库文档：{kb.title}",
                "detail_url": "/upload/",
                "fallback_url": "/upload/",
            }
        )

    items.sort(key=lambda x: x["time"] or datetime.min, reverse=True)
    out = []
    for it in items[:limit]:
        t = it["time"]
        out.append(
            {
                "kind": it["kind"],
                "kind_label": it["kind_label"],
                "time": t.strftime("%Y-%m-%d %H:%M") if t else "-",
                "content": it["content"],
                "detail_url": it["detail_url"],
                "fallback_url": it.get("fallback_url") or it["detail_url"],
            }
        )
    return out


def index(request):
    """首页：欢迎区 + 统计 + 快捷入口 + 动态/指引。"""
    project = (request.GET.get("project") or "").strip()
    valid = {k for k, _ in TestCase.BU_CHOICES}
    if project and project not in valid:
        project = ""

    stats = _build_stats(project)
    activities = _build_activities(project)
    context = {
        **stats,
        "projects": _project_choices(),
        "current_project": project,
        "activities": activities,
    }
    return render(request, "index.html", context)


@require_GET
def dashboard_stats(request):
    """切换项目后刷新统计与动态。"""
    project = (request.GET.get("project") or "").strip()
    valid = {k for k, _ in TestCase.BU_CHOICES}
    if project and project not in valid:
        project = ""
    return JsonResponse(
        {
            "success": True,
            "project": project,
            "stats": _build_stats(project),
            "activities": _build_activities(project),
        }
    )
