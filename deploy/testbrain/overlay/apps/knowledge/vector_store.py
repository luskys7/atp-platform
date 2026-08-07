from pymilvus import connections, Collection, utility, DataType
from pymilvus import CollectionSchema, FieldSchema
from typing import List, Dict, Any, Optional
from datetime import datetime
from django.conf import settings
from apps.utils.logger_manager import get_logger

logger = get_logger(__name__)


class MilvusVectorStore:
    """Milvus 向量库（含 add_documents 适配，供 KnowledgeService 调用）"""

    def __init__(
        self,
        host: Optional[str] = None,
        port: Optional[str] = None,
        db_name: Optional[str] = None,
        collection_name: Optional[str] = None,
    ):
        config = getattr(settings, "VECTOR_DB_CONFIG", {}) or {}
        self.host = host or config.get("host", "localhost")
        self.port = port or config.get("port", "19530")
        self.db_name = db_name or config.get("db_name", "default")
        self.collection_name = collection_name or config.get(
            "collection_name", "vv_knowledge_collection"
        )
        self._connect()
        self._ensure_collection()

    def _connect(self):
        connections.connect(
            alias="default", host=self.host, port=self.port, db_name=self.db_name
        )

    def _ensure_collection(self):
        logger.info("进入到_ensure_collection方法")
        if not utility.has_collection(self.collection_name):
            fields = [
                FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
                FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1024),
                FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=4096),
                FieldSchema(name="metadata", dtype=DataType.VARCHAR, max_length=1024),
                FieldSchema(name="source", dtype=DataType.VARCHAR, max_length=512),
                FieldSchema(name="doc_type", dtype=DataType.VARCHAR, max_length=32),
                FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, max_length=128),
                FieldSchema(name="upload_time", dtype=DataType.VARCHAR, max_length=50),
            ]
            schema = CollectionSchema(fields=fields, description="vv知识库")
            collection = Collection(name=self.collection_name, schema=schema)
            index_params = {
                "metric_type": "COSINE",
                "index_type": "HNSW",
                "params": {"M": 8, "efConstruction": 64},
            }
            collection.create_index(field_name="embedding", index_params=index_params)
            collection.load()
            return collection
        collection = Collection(self.collection_name)
        collection.load()
        return collection

    def add_data(self, data: List[Dict[str, Any]]):
        collection = Collection(self.collection_name)
        safe_data = []
        for item in data:
            content = item.get("content", "")
            if len(content) > 4096:
                item = dict(item)
                item["content"] = content[:4092] + "..."
            safe_data.append(item)
        collection.insert(safe_data)
        collection.flush()

    def add_documents(self, documents: List[Dict[str, Any]]):
        """兼容 KnowledgeService.add_knowledge 的 title/content/embedding 结构。"""
        rows = []
        now = datetime.now().isoformat()
        for i, doc in enumerate(documents):
            title = str(doc.get("title") or "untitled")
            content = str(doc.get("content") or "")
            emb = doc.get("embedding")
            if emb is None:
                continue
            if hasattr(emb, "tolist"):
                emb = emb.tolist()
            text = f"{title}\n{content}".strip()
            rows.append(
                {
                    "embedding": emb,
                    "content": text[:4092],
                    "metadata": "{}",
                    "source": title[:500],
                    "doc_type": "text",
                    "chunk_id": f"kb_{abs(hash(title)) % 10_000_000}_{i:04d}",
                    "upload_time": now,
                }
            )
        if rows:
            self.add_data(rows)

    def delete_by_source(self, source_path: str):
        collection = Collection(self.collection_name)
        expr = f'source == "{source_path}"'
        collection.delete(expr)
        collection.flush()

    def search(self, query_vector: List[float], top_k: int = 5) -> List[Dict[str, Any]]:
        collection = Collection(self.collection_name)
        collection.load()
        search_params = {"metric_type": "COSINE", "params": {"ef": 32}}
        results = collection.search(
            data=[query_vector],
            anns_field="embedding",
            param=search_params,
            limit=top_k,
            output_fields=[
                "content",
                "metadata",
                "source",
                "doc_type",
                "chunk_id",
                "upload_time",
            ],
        )
        ret = []
        for hits in results:
            for hit in hits:
                ret.append(
                    {
                        "id": hit.id,
                        "score": hit.score,
                        "content": hit.entity.get("content"),
                        "metadata": hit.entity.get("metadata"),
                        "source": hit.entity.get("source"),
                        "doc_type": hit.entity.get("doc_type"),
                        "chunk_id": hit.entity.get("chunk_id"),
                        "upload_time": hit.entity.get("upload_time"),
                    }
                )
        return ret
