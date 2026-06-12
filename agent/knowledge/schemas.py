from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class KnowledgeDocumentCreate(BaseModel):
    title: str = Field(..., min_length=1)
    content: str = Field(..., min_length=1)
    source_type: str = "manual_text"
    source_ref: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class KnowledgeDocumentCreateResponse(BaseModel):
    doc_id: str
    chunk_count: int
    status: str = "indexed"


class KnowledgeSearchRequest(BaseModel):
    query: str = Field(..., min_length=1)
    top_k: int = Field(default=6, ge=1, le=20)


class KnowledgeSearchItem(BaseModel):
    chunk_id: str
    doc_id: str
    title: str
    content: str
    score: float | None = None
    source_type: str | None = None
    source_ref: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class KnowledgeSearchResponse(BaseModel):
    items: list[KnowledgeSearchItem] = Field(default_factory=list)

