from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
from typing import Any

from openai import OpenAI


class KnowledgeEmbeddingClient:
    """Embedding and optional rerank client, using OpenAI-compatible APIs."""

    def __init__(
        self,
        base_url: str,
        api_key_env: str,
        embedding_model: str,
        embedding_dim: int,
        rerank_model: str | None = None,
        cache_path: str | None = None,
    ) -> None:
        api_key = os.getenv(api_key_env)
        if not api_key:
            raise RuntimeError(f"Missing embedding API key environment variable: {api_key_env}")
        self._client = OpenAI(base_url=base_url, api_key=api_key)
        self._embedding_model = embedding_model
        self._embedding_dim = embedding_dim
        self._rerank_model = rerank_model
        self._cache_path = Path(cache_path) if cache_path else None
        self._cache: dict[str, list[float]] = {}
        if self._cache_path and self._cache_path.exists():
            try:
                self._cache = json.loads(self._cache_path.read_text(encoding="utf-8"))
            except Exception:
                self._cache = {}

    def embed(self, texts: list[str]) -> list[list[float]]:
        results: list[list[float] | None] = []
        missing_texts: list[str] = []
        missing_indexes: list[int] = []
        for idx, text in enumerate(texts):
            key = self._cache_key(text)
            cached = self._cache.get(key)
            if cached is not None:
                results.append(cached)
            else:
                results.append(None)
                missing_texts.append(text)
                missing_indexes.append(idx)

        if missing_texts:
            response = self._client.embeddings.create(
                model=self._embedding_model,
                input=missing_texts,
                dimensions=self._embedding_dim,
            )
            for local_idx, item in enumerate(response.data):
                vector = list(item.embedding)
                original_idx = missing_indexes[local_idx]
                results[original_idx] = vector
                self._cache[self._cache_key(missing_texts[local_idx])] = vector
            self._flush_cache()

        return [r or [] for r in results]

    def rerank(self, query: str, docs: list[str], top_n: int) -> list[dict[str, Any]]:
        if not self._rerank_model or not docs:
            return [
                {"index": idx, "relevance_score": 1.0 - idx * 0.001}
                for idx, _ in enumerate(docs[:top_n])
            ]
        # DashScope-compatible rerank endpoints are not part of OpenAI SDK. Keep
        # this method intentionally conservative: if a dedicated rerank client is
        # introduced later, the service contract remains unchanged.
        return [
            {"index": idx, "relevance_score": 1.0 - idx * 0.001}
            for idx, _ in enumerate(docs[:top_n])
        ]

    @staticmethod
    def _cache_key(text: str) -> str:
        return hashlib.sha256(text.encode("utf-8")).hexdigest()

    def _flush_cache(self) -> None:
        if not self._cache_path:
            return
        self._cache_path.parent.mkdir(parents=True, exist_ok=True)
        self._cache_path.write_text(json.dumps(self._cache), encoding="utf-8")

