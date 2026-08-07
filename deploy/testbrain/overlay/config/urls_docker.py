"""ATP 嵌入用 URL：核心必开；可选模块按可导入再挂载；失败则挂友好占位页（避免 404）。"""
from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.contrib.staticfiles.urls import staticfiles_urlpatterns
from django.shortcuts import render
from django.urls import path, include, re_path

urlpatterns = [
    path("admin/", admin.site.urls),
    path("", include("apps.core.urls")),
    path("", include("apps.atp_bridge.urls")),
]

_OPTIONAL_UI = (
    ("test_case_generator/", "apps.ai_agents.test_case_generator.urls"),
    ("test_case_reviewer/", "apps.ai_agents.test_case_reviewer.urls"),
    ("prd_analyzer/", "apps.ai_agents.prd_analyzer.urls"),
    ("iface_case_generator/", "apps.ai_agents.iface_case_generator.urls"),
    ("java_code_analyzer/", "apps.ai_agents.java_code_analyzer.urls"),
)

_failed = []


def _make_stub(module_path: str, reason: str):
    def _view(request, rest=None):  # noqa: ARG001
        return render(
            request,
            "atp_module_unavailable.html",
            {"module_path": module_path, "reason": reason},
        )

    return _view


for prefix, module in _OPTIONAL_UI:
    try:
        __import__(module)
        urlpatterns.append(path(prefix, include(module)))
    except Exception as exc:  # noqa: BLE001
        print(f"[urls_docker] skip {prefix}: {exc}")
        _failed.append((prefix, str(exc)))

for prefix, reason in _failed:
    stub = _make_stub("/" + prefix, reason)
    urlpatterns.append(path(prefix, stub))
    urlpatterns.append(re_path(rf"^{prefix}(?P<rest>.*)$", stub))

if settings.DEBUG:
    urlpatterns += staticfiles_urlpatterns()
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
