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


class KnowledgeDocumentSummary(BaseModel):
    doc_id: str
    title: str
    source_type: str = ""
    source_ref: str = ""
    created_at: str = ""
    chunk_count: int = 0


class KnowledgeDocumentListResponse(BaseModel):
    documents: list[KnowledgeDocumentSummary] = Field(default_factory=list)


class KnowledgeDocumentDeleteResponse(BaseModel):
    doc_id: str
    deleted_chunks: int
    status: str = "deleted"


class KnowledgeFileUploadRequest(BaseModel):
    title: str = Field(..., min_length=1)
    file_name: str = Field(..., min_length=1)
    file_base64: str = Field(..., min_length=1)
    source_type: str = "uploaded_file"
    metadata: dict[str, Any] = Field(default_factory=dict)

