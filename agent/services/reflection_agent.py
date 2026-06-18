"""Reflection mode: ReAct loop + plan compliance check + quality evaluation with revision."""

from __future__ import annotations

import json
from typing import Generator

from .llm_service import LLMService
from .react_agent import ReActAgent
from .plan_checker import PlanChecker
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
    """ReAct agent with plan compliance check + self-evaluation and revision cycle."""

    def __init__(
        self,
        llm: LLMService,
        react_agent: ReActAgent,
        max_revisions: int = 1,
        max_check_retries: int = 2,
    ) -> None:
        self._llm = llm
        self._react = react_agent
        self._max_revisions = max_revisions
        self._plan_checker = PlanChecker(llm)
        self._max_check_retries = max_check_retries

    def run(
        self,
        query: str,
        file_summary: str = "",
        execution_plan: str = "",
        user_memory_markdown: str = "",
    ) -> Generator[str, None, None]:
        answer = yield from self._run_react(query, file_summary, execution_plan, user_memory_markdown)

        # Phase 1: Plan compliance check (structural validation)
        for check_attempt in range(self._max_check_retries):
            compliant, violations = self._plan_checker.check(query, answer)
            if compliant:
                yield sse_event(
                    "plan_check",
                    "结构合规检查通过。",
                    {"compliant": True, "attempt": check_attempt},
                )
                break

            yield sse_event(
                "plan_check",
                f"结构合规检查失败 ({check_attempt + 1}/{self._max_check_retries})：{'; '.join(violations[:3])}",
                {"compliant": False, "violations": violations, "attempt": check_attempt},
            )

            # Inject violation feedback and re-run
            violation_msg = "\n".join(f"- {v}" for v in violations)
            revised_plan = f"[PlanChecker 反馈] 方案存在结构问题，请修正：\n{violation_msg}"
            if execution_plan:
                revised_plan = f"{execution_plan}\n\n{revised_plan}"

            answer = yield from self._run_react(query, file_summary, revised_plan, user_memory_markdown)

        # Phase 2: Quality evaluation with revision cycle
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
