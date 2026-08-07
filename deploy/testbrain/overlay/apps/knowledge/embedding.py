"""Embedding 工厂：默认 aliyun；local(BGE-M3) 懒加载，避免镜像强制安装 torch。"""
from typing import List, Union
from abc import ABC, abstractmethod
import os
import time
import logging
import requests
import numpy as np
from concurrent.futures import ThreadPoolExecutor, as_completed
from django.conf import settings

logger = logging.getLogger(__name__)


class BaseEmbedder(ABC):
    @abstractmethod
    def get_embeddings(
        self, texts: Union[str, List[str]], show_progress_bar: bool = False
    ) -> List[List[float]]:
        pass


class BGEM3Embedder(BaseEmbedder):
    def __init__(self, model_name: str = "BAAI/bge-m3"):
        from sentence_transformers import SentenceTransformer  # lazy

        logger.info("正在加载 BGE-M3 模型...")
        self.model = SentenceTransformer(model_name)

    def get_embeddings(
        self, texts: Union[str, List[str]], show_progress_bar: bool = False
    ) -> List[List[float]]:
        if isinstance(texts, str):
            texts = [texts]
        embeddings = self.model.encode(
            sentences=texts, normalize_embeddings=True, show_progress_bar=show_progress_bar
        )
        return embeddings.tolist()

    def compute_similarity(self, text1: str, text2: str) -> float:
        embeddings = self.get_embeddings([text1, text2])
        return float(np.dot(embeddings[0], embeddings[1]))


class AliyunEmbedder(BaseEmbedder):
    def __init__(
        self, api_key: str = None, base_url: str = None, model: str = None, batch_size: int = None
    ):
        config = getattr(settings, "ALIYUN_EMBEDDING_CONFIG", {})
        self.api_key = api_key or config.get("api_key")
        self.base_url = base_url or config.get("base_url")
        self.model = model or config.get("model")
        self.batch_size = (
            batch_size if batch_size is not None else config.get("batch_size", 10)
        )
        self.embeddings_url = self.base_url
        if not self.api_key:
            raise ValueError("阿里云 API Key 未配置，请设置 QWEN_API_KEY")
        logger.info(
            f"阿里云嵌入模型初始化完成，模型: {self.model}, batch_size: {self.batch_size}"
        )

    def _get_embedding_batch(self, texts: List[str]) -> List[List[float]]:
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {"model": self.model, "input": texts, "dimensions": 1024}
        max_retries = 3
        for attempt in range(max_retries):
            try:
                response = requests.post(
                    self.embeddings_url, headers=headers, json=payload, timeout=60
                )
                response.raise_for_status()
                data = response.json()
                embeddings = [item.get("embedding", []) for item in data.get("data", [])]
                if len(embeddings) != len(texts):
                    raise ValueError("嵌入数量与文本数量不匹配")
                return embeddings
            except requests.exceptions.RequestException as e:
                logger.error(f"阿里云嵌入 API 失败 ({attempt + 1}/{max_retries}): {e}")
                if attempt == max_retries - 1:
                    raise
                time.sleep(1)
        return []

    def get_embeddings(
        self, texts: Union[str, List[str]], show_progress_bar: bool = False
    ) -> List[List[float]]:
        if isinstance(texts, str):
            texts = [texts]
        if not texts:
            return []
        batches = [
            texts[i : i + self.batch_size] for i in range(0, len(texts), self.batch_size)
        ]
        all_embeddings: List[List[float]] = [None] * len(texts)  # type: ignore
        with ThreadPoolExecutor(max_workers=min(5, len(batches) or 1)) as executor:
            futures = {
                executor.submit(self._get_embedding_batch, batch): (idx, batch)
                for idx, batch in enumerate(batches)
            }
            for future in as_completed(futures):
                batch_idx, batch = futures[future]
                batch_embeddings = future.result()
                start = batch_idx * self.batch_size
                for i, emb in enumerate(batch_embeddings):
                    all_embeddings[start + i] = emb
        return all_embeddings  # type: ignore


def create_embedder():
    provider = getattr(settings, "EMBEDDING_PROVIDER", "aliyun")
    if provider == "aliyun":
        logger.info("使用阿里云向量模型")
        return AliyunEmbedder()
    logger.info("使用本地 BGE-M3 向量模型")
    return BGEM3Embedder()
