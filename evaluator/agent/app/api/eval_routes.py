"""HTTP routes for evaluation. Currently exposes a Ragas scoring endpoint
that the Java backend calls per evaluation run."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.ragas_service import (
    RagasSample,
    SUPPORTED_METRICS,
    get_ragas_service,
)

router = APIRouter(prefix="/eval", tags=["eval"])


class RagasSamplePayload(BaseModel):
    question: str = Field(..., min_length=1)
    answer: str = Field("", description="The agent / model output to be judged")
    ground_truth: str = Field("", alias="groundTruth")
    contexts: list[str] | None = Field(default=None)

    model_config = {"populate_by_name": True}


class RagasScoreRequest(BaseModel):
    samples: list[RagasSamplePayload]
    metrics: list[str] | None = Field(
        default=None,
        description="Subset of supported metrics; defaults to all",
    )


class RagasScoreResponse(BaseModel):
    scores: dict[str, list[float]]
    mean: dict[str, float]
    warning: str | None = None


@router.post("/ragas/score", response_model=RagasScoreResponse)
def ragas_score(payload: RagasScoreRequest) -> RagasScoreResponse:
    if not payload.samples:
        raise HTTPException(status_code=400, detail="samples must not be empty")

    requested = payload.metrics or list(SUPPORTED_METRICS)
    bad = [m for m in requested if m not in SUPPORTED_METRICS]
    if bad:
        raise HTTPException(
            status_code=400,
            detail=f"unsupported metrics: {bad}; allowed={sorted(SUPPORTED_METRICS)}",
        )

    samples = [
        RagasSample(
            question=s.question,
            answer=s.answer or "",
            ground_truth=s.ground_truth or "",
            contexts=s.contexts,
        )
        for s in payload.samples
    ]

    result = get_ragas_service().score(samples, requested)
    return RagasScoreResponse(
        scores=result.scores,
        mean=result.mean,
        warning=result.warning,
    )


@router.get("/ragas/metrics")
def list_supported_metrics() -> dict[str, list[str]]:
    return {"metrics": sorted(SUPPORTED_METRICS)}
