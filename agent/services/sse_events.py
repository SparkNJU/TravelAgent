"""SSE event formatting helpers."""

from __future__ import annotations

import json
from typing import Any


def sse_event(event_type: str, content: str, metadata: dict[str, Any] | None = None) -> str:
    """Format a structured SSE event as a data line.

    Event types:
    - "plan": meta-plan content (plan mode)
    - "thought": agent reasoning step
    - "action": tool invocation
    - "observation": tool result
    - "ask_user": agent is waiting for user input (questions in metadata.questions)
    - "suggestions": follow-up question suggestions (questions in metadata.questions)
    - "answer": final travel plan
    - "error": error message
    - "done": stream complete signal
    """
    payload = {
        "type": event_type,
        "content": content,
        "metadata": metadata or {},
    }
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


SSE_DONE = "data: [DONE]\n\n"
