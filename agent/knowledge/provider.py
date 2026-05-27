from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from config import config

from .embedding_client import KnowledgeEmbeddingClient
from .service import KnowledgeService
from .splitter import TextSplitter
from .vector_store import MilvusKnowledgeStore


@lru_cache(maxsize=1)
def get_knowledge_service() -> KnowledgeService:
    cache_path = Path(__file__).resolve().parents[1] / ".knowledge_embedding_cache.json"
    store = MilvusKnowledgeStore(
        uri=config.knowledge.milvus_uri,
        collection=config.knowledge.collection,
        embedding_dim=config.knowledge.embedding_dim,
        dense_weight=config.knowledge.dense_weight,
        sparse_weight=config.knowledge.sparse_weight,
    )
    embedding = KnowledgeEmbeddingClient(
        base_url=config.llm.base_url,
        api_key_env=config.llm.api_key_env,
        embedding_model=config.knowledge.embedding_model,
        embedding_dim=config.knowledge.embedding_dim,
        rerank_model=config.knowledge.rerank_model if config.knowledge.rerank_enabled else None,
        cache_path=str(cache_path),
    )
    return KnowledgeService(
        vector_store=store,
        embedding_client=embedding,
        splitter=TextSplitter(
            chunk_size=config.knowledge.chunk_size,
            chunk_overlap=config.knowledge.chunk_overlap,
        ),
        namespace=config.knowledge.namespace,
        recall_limit=config.knowledge.recall_limit,
    )

