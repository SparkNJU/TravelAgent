"""Reflection mode: ReAct loop + self-evaluation with revision if needed."""

from __future__ import annotations

import json
from typing import Generator

from .llm_service import LLMService
from .react_agent import ReActAgent
from .sse_events import sse_event

_EVALUATION_SCHEMA = {
    "name": "travel_plan_evaluation",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "verdict": {
                "type": "string",
                "enum": ["satisfactory", "needs_improvement"],
            },
            "issues": {
                "type": "array",
                "items": {"type": "string"},
            },
            "suggestions": {"type": "string"},
        },
        "required": ["verdict", "issues", "suggestions"],
        "additionalProperties": False,
    },
}

_EVAL_PROMPT = """\
You are a travel plan quality evaluator. Assess the following travel plan.

User request: {query}

Generated plan:
{answer}

Evaluate whether the plan is:
1. Complete — covers the full trip duration with day-by-day details
2. Specific — includes real attraction names, restaurants, transit info
3. Accurate — information is realistic and up-to-date
4. Well-structured — logically organized with clear daily breakdown

Return a JSON evaluation with:
- verdict: "satisfactory" if quality is acceptable, "needs_improvement" if gaps exist
- issues: list of specific problems found
- suggestions: concrete instructions for the re-generation attempt"""


class ReflectionAgent:
    """ReAct agent with a post-execution self-reflection and revision cycle."""

    def __init__(
        self,
        llm: LLMService,
        react_agent: ReActAgent,
        max_revisions: int = 1,
    ) -> None:
        self._llm = llm
        self._react = react_agent
        self._max_revisions = max_revisions

    def run(
        self,
        query: str,
        file_summary: str = "",
        execution_plan: str = "",
        user_memory_markdown: str = "",
    ) -> Generator[str, None, None]:
        answer = yield from self._run_react(query, file_summary, execution_plan, user_memory_markdown)

        for revision in range(self._max_revisions):
            verdict, issues, suggestions = self._evaluate(query, answer)

            if verdict == "satisfactory":
                yield sse_event(
                    "reflection",
                    "自我评估：方案质量达标。",
                    {"verdict": "satisfactory", "revision": revision},
                )
                return

            yield sse_event(
                "reflection",
                f"发现 {len(issues)} 个问题，正在改进：{suggestions[:200]}",
                {"verdict": "needs_improvement", "revision": revision},
            )

            revised_plan = execution_plan
            if execution_plan:
                revised_plan += f"\n\n[反思改进] 前一轮问题：{'；'.join(issues)}\n改进建议：{suggestions}"
            else:
                revised_plan = f"[反思改进] 前一轮问题：{'；'.join(issues)}\n改进建议：{suggestions}"

            answer = yield from self._run_react(query, file_summary, revised_plan, user_memory_markdown)

        yield sse_event(
            "reflection",
            "已达最大修正次数，使用当前版本。",
            {"verdict": "max_revisions_reached", "revision": self._max_revisions},
        )

    def _run_react(
        self, query: str, file_summary: str, execution_plan: str, user_memory_markdown: str
    ) -> Generator[str, None, str]:
        answer = ""
        for event_json in self._react.run(
            query,
            file_summary,
            execution_plan,
            user_memory_markdown=user_memory_markdown,
        ):
            event = json.loads(event_json)
            if event.get("type") == "answer":
                answer = event.get("content", "")
            yield f"data: {event_json}\n\n"
        return answer

    def _evaluate(self, query: str, answer: str) -> tuple[str, list[str], str]:
        try:
            result = self._llm.chat_json(
                messages=[
                    {"role": "system", "content": "You are a strict travel plan evaluator. Respond only with the requested JSON."},
                    {"role": "user", "content": _EVAL_PROMPT.format(query=query, answer=answer)},
                ],
                json_schema=_EVALUATION_SCHEMA,
            )
            return (
                result.get("verdict", "satisfactory"),
                result.get("issues", []),
                result.get("suggestions", ""),
            )
        except Exception:
            return "satisfactory", [], ""
