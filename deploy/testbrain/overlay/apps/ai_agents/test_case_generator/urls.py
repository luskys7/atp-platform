from django.urls import path
from django.views.decorators.csrf import csrf_exempt

from . import views

app_name = "test_case_generator"

urlpatterns = [
    path("", csrf_exempt(views.generate), name="generate"),
    path("save-test-case/", csrf_exempt(views.save_test_case), name="save_test_case"),
    path("api/knowledge-docs/", csrf_exempt(views.list_knowledge_docs), name="knowledge_docs"),
    path("api/export-excel/", csrf_exempt(views.export_generated_excel), name="export_generated_excel"),
]
