"""Ragas-based evaluation service.

Wraps a small subset of the Ragas library so the Java backend can score
generated answers via HTTP. Only LLM-driven metrics are exposed here so
the runtime does not need an embedding model:

  - faithfulness        — answer is grounded in the provided contexts
  - answer_correctness  — answer matches ground truth (LLM-judged)

The LLM is the same ModelScope-compatible OpenAI endpoint already used by
the Java backend (configured via MODELSCOPE_API_KEY + MODELSCOPE_BASE_URL).

Failures are converted to a fallback score (0.5) plus a `warning` field so
a Ragas outage never blocks a run.
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from typing import Iterable

logger = logging.getLogger(__name__)

DEFAULT_BASE_URL = "https://api-inference.modelscope.cn/v1"
DEFAULT_JUDGE_MODEL = "Qwen/Qwen3-235B-A22B-Instruct-2507"
SUPPORTED_METRICS = {"faithfulness", "answer_correctness"}
FALLBACK_SCORE = 0.5


def _is_nan(value) -> bool:
    try:
        return value != value  # NaN is the only float that is not equal to itself
    except Exception:
        return False


@dataclass
class RagasSample:
    question: str
    answer: str
    ground_truth: str = ""
    contexts: list[str] | None = None


@dataclass
class RagasScoreResult:
    """Per-metric per-sample scores plus a mean across samples."""

    scores: dict[str, list[float]]
    mean: dict[str, float]
    warning: str | None = None


class RagasService:
    """Lazy-init wrapper. Heavy imports happen on first call so the FastAPI
    app starts fast even when ragas is not installed yet."""

    def __init__(self) -> None:
        self._llm = None
        self._embeddings = None
        self._metric_objs: dict[str, object] = {}

    def _get_llm(self):
        if self._llm is not None:
            return self._llm

        from langchain_openai import ChatOpenAI
        from ragas.llms import LangchainLLMWrapper

        api_key = os.environ.get("MODELSCOPE_API_KEY") or os.environ.get(
            "MODELSCOPE_ACCESS_TOKEN"
        )
        base_url = os.environ.get("MODELSCOPE_BASE_URL", DEFAULT_BASE_URL)
        model_id = os.environ.get("RAGAS_JUDGE_MODEL", DEFAULT_JUDGE_MODEL)
        if not api_key:
            raise RuntimeError(
                "MODELSCOPE_API_KEY not set; ragas evaluation cannot run."
            )

        # Some ragas internals fall back to a default OpenAI client when our
        # wrapper does not cover every code path. Mirror the ModelScope
        # credentials onto the OpenAI env vars so those fallbacks reach
        # ModelScope's OpenAI-compatible endpoint instead of api.openai.com.
        os.environ.setdefault("OPENAI_API_KEY", api_key)
        os.environ.setdefault("OPENAI_BASE_URL", base_url)
        os.environ.setdefault("OPENAI_API_BASE", base_url)

        chat = ChatOpenAI(
            model=model_id,
            openai_api_key=api_key,
            openai_api_base=base_url,
            temperature=0.0,
            timeout=60,
            max_retries=2,
        )
        self._llm = LangchainLLMWrapper(chat)
        return self._llm

    def _get_metric(self, name: str):
        if name in self._metric_objs:
            return self._metric_objs[name]

        from ragas.metrics import answer_correctness, faithfulness

        catalog = {
            "faithfulness": faithfulness,
            "answer_correctness": answer_correctness,
        }
        metric = catalog[name]
        # Inject our LLM so ragas does not try to reach OpenAI directly.
        metric.llm = self._get_llm()
        self._metric_objs[name] = metric
        return metric

    def score(
        self,
        samples: list[RagasSample],
        metrics: Iterable[str],
    ) -> RagasScoreResult:
        wanted = [m for m in metrics if m in SUPPORTED_METRICS]
        if not wanted:
            wanted = ["faithfulness", "answer_correctness"]

        if not samples:
            empty = {m: [] for m in wanted}
            return RagasScoreResult(scores=empty, mean={m: 0.0 for m in wanted})

        try:
            from datasets import Dataset
            from ragas import evaluate
        except Exception as exc:  # pragma: no cover - import-time fallback
            logger.exception("ragas import failed: %s", exc)
            return self._fallback(samples, wanted, f"ragas import failed: {exc}")

        try:
            data = {
                "question": [s.question for s in samples],
                "answer": [s.answer for s in samples],
                "ground_truth": [s.ground_truth or "" for s in samples],
                "contexts": [
                    s.contexts if s.contexts else [s.ground_truth or s.answer]
                    for s in samples
                ],
            }
            dataset = Dataset.from_dict(data)
            metric_objs = [self._get_metric(name) for name in wanted]
            result = evaluate(dataset=dataset, metrics=metric_objs, llm=self._get_llm())
            df = result.to_pandas()
            print(
                "[ragas] columns=",
                df.columns.tolist(),
                "\n[ragas] head=\n",
                df.to_dict(),
                flush=True,
            )

            scores: dict[str, list[float]] = {}
            mean: dict[str, float] = {}
            nan_metrics: list[str] = []
            for name in wanted:
                if name not in df.columns:
                    scores[name] = [FALLBACK_SCORE] * len(samples)
                    mean[name] = FALLBACK_SCORE
                    nan_metrics.append(f"{name}(missing)")
                    continue
                raw_col = df[name].tolist()
                if any(_is_nan(v) for v in raw_col):
                    nan_metrics.append(f"{name}({sum(1 for v in raw_col if _is_nan(v))}/{len(raw_col)} NaN)")
                col = df[name].fillna(FALLBACK_SCORE).tolist()
                scores[name] = [float(v) for v in col]
                mean[name] = float(sum(scores[name]) / len(scores[name])) if scores[name] else 0.0
            warning = None
            if nan_metrics:
                warning = "ragas produced NaN, fell back to 0.5: " + ", ".join(nan_metrics)
            return RagasScoreResult(scores=scores, mean=mean, warning=warning)
        except Exception as exc:
            logger.exception("ragas evaluate failed: %s", exc)
            return self._fallback(samples, wanted, f"ragas evaluate failed: {exc}")

    def _fallback(
        self,
        samples: list[RagasSample],
        metrics: list[str],
        warning: str,
    ) -> RagasScoreResult:
        scores = {m: [FALLBACK_SCORE] * len(samples) for m in metrics}
        mean = {m: FALLBACK_SCORE for m in metrics}
        return RagasScoreResult(scores=scores, mean=mean, warning=warning)


_singleton = RagasService()


def get_ragas_service() -> RagasService:
    return _singleton
