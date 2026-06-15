from __future__ import annotations

from fastapi import APIRouter, HTTPException

from .provider import get_knowledge_service
from .schemas import KnowledgeDocumentCreate, KnowledgeFileUploadRequest, KnowledgeSearchRequest

router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])


@router.get("/health")
def knowledge_health() -> dict:
    return {"ok": True, "mode": "embedded"}


@router.post("/documents")
def create_knowledge_document(request: KnowledgeDocumentCreate) -> dict:
    try:
        return get_knowledge_service().ingest_text(request).model_dump()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/documents/upload")
def upload_knowledge_document(request: KnowledgeFileUploadRequest) -> dict:
    try:
        return get_knowledge_service().ingest_file(request).model_dump()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.get("/documents")
def list_knowledge_documents() -> dict:
    try:
        return get_knowledge_service().list_documents().model_dump()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.delete("/documents/{doc_id}")
def delete_knowledge_document(doc_id: str) -> dict:
    try:
        return get_knowledge_service().delete_document(doc_id).model_dump()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/search")
def search_knowledge(request: KnowledgeSearchRequest) -> dict:
    try:
        return get_knowledge_service().search(request).model_dump()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
