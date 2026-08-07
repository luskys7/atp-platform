from django.apps import AppConfig

class KnowledgeConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.knowledge"

    embedder = None
    vector_store = None

    def ready(self):
        from .embedding import create_embedder
        from .vector_store import MilvusVectorStore

        if KnowledgeConfig.embedder is None:
            KnowledgeConfig.embedder = create_embedder()
        if KnowledgeConfig.vector_store is None:
            KnowledgeConfig.vector_store = MilvusVectorStore()