"""Plan mode: generates an execution plan for the ReAct agent to follow."""

from __future__ import annotations

import json
from typing import Generator

from .llm_service import LLMService

_SYSTEM_PROMPT = """\
You are a travel planning strategist. Given a user's travel request, generate a step-by-step \
execution plan that an AI agent will follow to create a detailed travel itinerary.

Available tools:
- web_search: search the web for attractions, food, transportation, etc.
- get_weather: get current weather and forecast for a city
- parse_file: extract text from an uploaded file (txt/pdf/docx)

For each step, specify:
1. What needs to be done
2. Which tool to use (web_search, get_weather, parse_file, or "reasoning" for LLM reasoning)
3. What information is expected from that step

Output a numbered list of steps. Be specific about search queries and reasoning tasks. \
Keep the plan concise (5-8 steps)."""


class MetaPlanner:
    """Plan mode: uses LLM to generate a meta-plan describing what steps the agent should take."""

    def __init__(self, llm: LLMService) -> None:
        self._llm = llm

    def generate_plan(
        self, query: str, file_summary: str = ""
    ) -> Generator[str, None, None]:
        """Generate a meta-plan. Yields SSE event JSON strings (type: "plan")."""
        user_content = f"User request: {query}"
        if file_summary:
            user_content += f"\n\nAdditional context from uploaded file:\n{file_summary}"

        messages = [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ]

        for chunk_json in self._llm.chat_stream(messages):
            chunk = json.loads(chunk_json)
            choices = chunk.get("choices", [])
            if not choices:
                continue
            delta = choices[0].get("delta", {})
            content = delta.get("content")
            if content:
                yield json.dumps(
                    {"type": "plan", "content": content, "metadata": {}},
                    ensure_ascii=False,
                )
