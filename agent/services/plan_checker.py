"""LLM-based plan structural compliance checker.

Runs before the final answer is emitted. If violations are found,
the feedback is injected back into the ReAct loop for self-correction.
"""

from __future__ import annotations

import json
import logging

from .llm_service import LLMService

logger = logging.getLogger(__name__)

_CHECKER_SCHEMA = {
    "name": "plan_compliance_check",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "compliant": {"type": "boolean"},
            "violations": {
                "type": "array",
                "items": {"type": "string"},
            },
        },
        "required": ["compliant", "violations"],
        "additionalProperties": False,
    },
}

_CHECKER_PROMPT = """\
You are a strict travel plan compliance checker. Your job is to validate a travel plan \
against structural rules BEFORE it is presented to the user.

User request: {query}

Travel plan to check:
{plan}

Check the following rules carefully:

1. **Closed-loop trip**: The trip must return to the departure city. \
If the user departs from city A, the last day must end in city A (or have transportation back to city A).

2. **No repeated restaurants**: Each restaurant (for breakfast, lunch, dinner) should only appear \
once in the entire itinerary. The same restaurant should NOT be used for multiple meals across different days.

3. **No repeated attractions**: Each attraction should only be visited once across all days.

4. **Transportation consistency**: The trip should use a consistent mode of transportation. \
Mixing flights on one leg and self-driving on another leg of the same trip is not acceptable \
unless explicitly requested by the user.

5. **Accommodation reasonableness**: If an accommodation has a minimum nights requirement, \
the stay should meet that requirement. Also, the traveler should stay in the destination city, \
not in the origin city.

6. **Day coverage**: The plan must cover ALL days requested by the user. \
If the user asks for a 5-day trip, there must be 5 days of content.

7. **City coverage**: If the user requests visiting N cities in the destination state, \
the plan should visit exactly N distinct cities (not counting the origin city).

8. **Meal completeness**: On non-travel days (days without long-distance transportation), \
all three meals (breakfast, lunch, dinner) should be planned.

Return a JSON object with:
- "compliant": true if ALL rules pass, false if ANY rule fails
- "violations": list of specific violation descriptions (empty if compliant). \
Each violation should clearly state what rule was broken and which day(s) are affected.
"""

_MAX_CHECK_RETRIES = 2


class PlanChecker:
    """Validates a travel plan for structural compliance using LLM."""

    def __init__(self, llm: LLMService) -> None:
        self._llm = llm

    def check(self, query: str, plan_markdown: str) -> tuple[bool, list[str]]:
        """Check a travel plan for structural violations.

        Returns:
            (compliant, violations) — compliant is True if no violations found.
        """
        try:
            result = self._llm.chat_json(
                messages=[
                    {
                        "role": "system",
                        "content": "You are a strict travel plan compliance checker. Respond only with the requested JSON.",
                    },
                    {
                        "role": "user",
                        "content": _CHECKER_PROMPT.format(
                            query=query, plan=plan_markdown
                        ),
                    },
                ],
                json_schema=_CHECKER_SCHEMA,
            )
            compliant = result.get("compliant", True)
            violations = result.get("violations", [])
            return compliant, violations
        except Exception as e:
            logger.warning(f"PlanChecker failed, assuming compliant: {e}")
            return True, []
