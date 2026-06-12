from __future__ import annotations

import time
import uuid
from datetime import datetime, timezone
from typing import Any

from .schemas import (
    KnowledgeDocumentCreate,
    KnowledgeDocumentCreateResponse,
    KnowledgeSearchItem,
    KnowledgeSearchRequest,
    KnowledgeSearchResponse,
)
from .splitter import TextSplitter


class KnowledgeService:
    def __init__(
        self,
        vector_store: Any,
        embedding_client: Any,
        splitter: TextSplitter,
        namespace: str = "default",
        recall_limit: int = 30,
    ) -> None:
        self._vector_store = vector_store
        self._embedding_client = embedding_client
        self._splitter = splitter
        self._namespace = namespace
        self._recall_limit = recall_limit

    def ingest_text(self, document: KnowledgeDocumentCreate) -> KnowledgeDocumentCreateResponse:
        chunks = self._splitter.split_text(document.content)
        if not chunks:
            raise ValueError("content has no indexable text")
        doc_id = self._new_doc_id()
        embeddings = self._embedding_client.embed(chunks)
        created_at = datetime.now(timezone.utc).isoformat()
        rows: list[dict[str, Any]] = []
        source_ref = document.source_ref or ""
        for index, (chunk, embedding) in enumerate(zip(chunks, embeddings), start=1):
            rows.append(
                {
                    "chunk_id": f"{doc_id}_{index}_{uuid.uuid4().hex[:8]}",
                    "doc_id": doc_id,
                    "doc_title": document.title,
                    "namespace": self._namespace,
                    "source_type": document.source_type,
                    "source_ref": source_ref,
                    "content": chunk,
                    "metadata": document.metadata,
                    "created_at": created_at,
                    "embedding": embedding,
                }
            )
        inserted = self._vector_store.insert(rows)
        return KnowledgeDocumentCreateResponse(doc_id=doc_id, chunk_count=inserted)

    def search(self, request: KnowledgeSearchRequest) -> KnowledgeSearchResponse:
        query_vector = self._embedding_client.embed([request.query])[0]
        hits = self._vector_store.hybrid_search(
            query_vector=query_vector,
            query_text=request.query,
            namespace=self._namespace,
            limit=max(self._recall_limit, request.top_k),
        )
        docs = [hit.get("content") or "" for hit in hits]
        reranked = self._embedding_client.rerank(request.query, docs, top_n=request.top_k)
        items: list[KnowledgeSearchItem] = []
        for rank in reranked:
            index = int(rank.get("index", -1))
            if index < 0 or index >= len(hits):
                continue
            hit = hits[index]
            items.append(
                KnowledgeSearchItem(
                    chunk_id=str(hit.get("chunk_id") or ""),
                    doc_id=str(hit.get("doc_id") or ""),
                    title=str(hit.get("doc_title") or hit.get("title") or "知识片段"),
                    content=str(hit.get("content") or ""),
                    score=rank.get("relevance_score", hit.get("score")),
                    source_type=hit.get("source_type"),
                    source_ref=hit.get("source_ref"),
                    metadata=hit.get("metadata") or {},
                )
            )
        return KnowledgeSearchResponse(items=items)

    @staticmethod
    def _new_doc_id() -> str:
        return f"kc_{int(time.time() * 1000)}_{uuid.uuid4().hex[:10]}"

