"""Parse agent Markdown travel plans into TravelPlanner structured JSON using LLM."""

from __future__ import annotations

import json
import os
import re

from openai import OpenAI

# Import project config
import sys
from pathlib import Path

_BENCHMARK_DIR = Path(__file__).resolve().parent
_AGENT_ROOT = _BENCHMARK_DIR.parent
sys.path.insert(0, str(_AGENT_ROOT))
from config import config

# System prompt for the LLM parser
PARSE_SYSTEM_PROMPT = """You are a data extraction assistant. Your job is to convert a Markdown travel plan into a structured JSON array.

Output ONLY a valid JSON array (no markdown fences, no explanation). Each element represents one day:

```json
[
  {
    "day": 1,
    "current_city": "from Washington to Myrtle Beach",
    "transportation": "Flight Number: F3927581, from Washington to Myrtle Beach, Departure Time: 11:03, Arrival Time: 13:31",
    "breakfast": "-",
    "lunch": "Catfish Charlie's, Myrtle Beach",
    "dinner": "d' Curry House, Myrtle Beach",
    "attraction": "SkyWheel Myrtle Beach, Myrtle Beach;",
    "accommodation": "Adorable Prospect Heights 1 Bedroom, Myrtle Beach"
  },
  {
    "day": 2,
    "current_city": "Myrtle Beach",
    "transportation": "-",
    "breakfast": "Restaurant A, Myrtle Beach",
    "lunch": "Restaurant B, Myrtle Beach",
    "dinner": "Restaurant C, Myrtle Beach",
    "attraction": "Place A, Myrtle Beach;Place B, Myrtle Beach;",
    "accommodation": "Adorable Prospect Heights 1 Bedroom, Myrtle Beach"
  },
  {
    "day": 3,
    "current_city": "from Myrtle Beach to Washington",
    "transportation": "Self-driving from Myrtle Beach to Washington, Distance: 600 km, Duration: 6 hours",
    "breakfast": "Restaurant D, Myrtle Beach",
    "lunch": "-",
    "dinner": "-",
    "attraction": "-",
    "accommodation": "-"
  }
]
```

Rules:
1. Each day MUST have exactly these 7 keys: day, current_city, transportation, breakfast, lunch, dinner, attraction, accommodation.
2. "day" is an integer starting from 1.
3. "current_city" should be "from X to Y" for travel days, or just the city name otherwise.
4. "transportation" MUST always include "from X to Y" with the origin and destination city names. For flights: "Flight Number: XXXXX, from CityA to CityB, Departure Time: HH:MM, Arrival Time: HH:MM". For self-driving: "Self-driving from CityA to CityB, Distance: X km, Duration: X hours". For taxi: "Taxi from CityA to CityB". Use "-" only if no inter-city transportation is needed on that day.
5. "attraction" must end with a semicolon after each attraction name. E.g. "Place A, City;Place B, City;". Use "-" if none.
6. For meals and accommodation, include the city name. E.g. "Restaurant Name, City". Use "-" if not needed.
7. Accommodation should be "-" on the last day.
8. Transportation should be "-" for days without inter-city travel.
9. All entity names must exactly match what appears in the plan. Do NOT invent or modify names.
10. Output ONLY the JSON array, nothing else."""


def parse_plan_with_llm(
    markdown_plan: str,
    llm_base_url: str | None = None,
    llm_api_key: str | None = None,
    llm_model: str | None = None,
    max_retries: int = 2,
) -> list[dict] | None:
    """
    Use LLM to convert a Markdown travel plan into structured JSON.

    Args:
        markdown_plan: The agent's Markdown travel plan.
        llm_base_url: OpenAI-compatible API base URL. If None, reads from config.
        llm_api_key: API key (if None, reads from config env var).
        llm_model: Model name. If None, reads from config.
        max_retries: Number of retries on parse failure.

    Returns:
        List of day dicts, or None if parsing fails.
    """
    # Use config defaults if not provided
    base_url = llm_base_url or config.llm.base_url
    model = llm_model or config.llm.chat_model

    # Resolve API key from env var specified in config
    api_key = llm_api_key or os.getenv(config.llm.api_key_env, "")
    if not api_key:
        raise RuntimeError(f"No API key found. Set {config.llm.api_key_env} env var.")

    client = OpenAI(api_key=api_key, base_url=base_url)

    user_prompt = f"Convert the following travel plan to JSON:\n\n{markdown_plan}"

    for attempt in range(max_retries + 1):
        try:
            resp = client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": PARSE_SYSTEM_PROMPT},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=0.0,
                max_tokens=4096,
            )
            content = resp.choices[0].message.content.strip()
            # Strip markdown code fences if present
            content = re.sub(r"^```(?:json)?\s*", "", content)
            content = re.sub(r"\s*```$", "", content)
            result = json.loads(content)
            if isinstance(result, list) and len(result) > 0:
                return result
        except (json.JSONDecodeError, Exception) as e:
            if attempt == max_retries:
                print(f"  [WARN] LLM parse failed after {max_retries + 1} attempts: {e}")
                return None
    return None
