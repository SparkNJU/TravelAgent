from __future__ import annotations

from typing import Any, Callable

import requests

from .schemas import KnowledgeSearchRequest


class InProcessKnowledgeClient:
    def __init__(self, service_factory: Callable[[], Any]) -> None:
        self._service_factory = service_factory

    def search(self, query: str, top_k: int = 6) -> dict[str, Any]:
        response = self._service_factory().search(KnowledgeSearchRequest(query=query, top_k=top_k))
        return response.model_dump()


class HttpKnowledgeClient:
    def __init__(self, base_url: str, timeout: float = 10.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout

    def search(self, query: str, top_k: int = 6) -> dict[str, Any]:
        resp = requests.post(
            f"{self._base_url}/search",
            json={"query": query, "top_k": top_k},
            timeout=self._timeout,
        )
        resp.raise_for_status()
        return resp.json()

