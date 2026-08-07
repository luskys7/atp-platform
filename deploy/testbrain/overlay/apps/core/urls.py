from django.urls import path
from . import views
from . import dashboard_views
from . import upload_views
from .views_sse import stream_logs

urlpatterns = [
    path('', dashboard_views.index, name='index'),
    path('api/dashboard-stats/', dashboard_views.dashboard_stats, name='dashboard_stats'),

    path('knowledge/', views.knowledge_view, name='knowledge'),
    path('upload/', upload_views.upload_page, name='upload_single_file'),
    path('api/upload-knowledge/', upload_views.upload_file, name='upload_knowledge_api'),

    path('api/add-knowledge/', views.add_knowledge, name='add_knowledge'),
    path('api/knowledge-list/', views.knowledge_list, name='knowledge_list'),
    path('api/search-knowledge/', views.search_knowledge, name='search_knowledge'),
    path('api/stream-logs/', stream_logs, name='stream_logs'),
]
