from django.apps import AppConfig
import logging

logger = logging.getLogger(__name__)


class KnowledgeConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.knowledge"

    embedder = None
    vector_store = None

    def ready(self):
        from .embedding import create_embedder
        from .vector_store import MilvusVectorStore

        if KnowledgeConfig.embedder is None:
            try:
                KnowledgeConfig.embedder = create_embedder()
            except Exception as e:
                logger.error("Embedding 初始化失败: %s", e)
                KnowledgeConfig.embedder = None
        if KnowledgeConfig.vector_store is None:
            try:
                KnowledgeConfig.vector_store = MilvusVectorStore()
            except Exception as e:
                logger.error("Milvus 初始化失败: %s", e)
                KnowledgeConfig.vector_store = None
