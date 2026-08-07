from django.urls import path
from . import views

urlpatterns = [
    path("api/v1/health", views.health),
    path("api/v1/ai/generate-cases", views.generate_cases),
    path("api/v1/ai/knowledge/ingest", views.knowledge_ingest),
    path("api/v1/ai/knowledge/list", views.knowledge_list),
]
