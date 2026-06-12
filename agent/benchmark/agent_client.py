"""Client for calling the TravelMind agent API and extracting travel plans."""

from __future__ import annotations

import json
import httpx


async def call_agent(
    query: str,
    agent_url: str = "http://localhost:8000",
    timeout: float = 120.0,
    mode: str = "agent",
    generate_plan_first: bool = True,
) -> str | None:
    """
    Send a query to the agent and return the Markdown travel plan.

    Args:
        query: The travel planning query.
        agent_url: Base URL of the agent service.
        timeout: Request timeout in seconds.
        mode: Agent mode ("agent", "plan", "reflection").
        generate_plan_first: Whether to run MetaPlanner before the agent.

    Returns:
        The Markdown travel plan string, or None if no answer was produced.
    """
    payload = {
        "query": query,
        "user_id": 0,  # benchmark user
        "mode": mode,
        "generate_plan_first": generate_plan_first,
        "arena": True,  # disable ask_user and suggest_questions
        "chat_history": [],
    }

    answer_text = ""
    async with httpx.AsyncClient(timeout=httpx.Timeout(timeout, connect=10.0)) as client:
        async with client.stream(
            "POST",
            f"{agent_url}/api/agent/chat",
            json=payload,
            headers={"Accept": "text/event-stream"},
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line.startswith("data: "):
                    continue
                data_str = line[len("data: "):]
                if data_str.strip() == "[DONE]":
                    break
                try:
                    event = json.loads(data_str)
                except json.JSONDecodeError:
                    continue
                if event.get("type") == "answer":
                    answer_text += event.get("content", "")
                elif event.get("type") == "error":
                    raise RuntimeError(f"Agent error: {event.get('content')}")

    return answer_text.strip() if answer_text.strip() else None
