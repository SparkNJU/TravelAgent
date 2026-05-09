"""Weather API tool using weatherapi.com."""

from __future__ import annotations

import requests

from .tool_registry import Tool


class WeatherTool(Tool):
    def __init__(self, api_key: str, base_url: str = "https://api.weatherapi.com/v1") -> None:
        self._api_key = api_key
        self._base_url = base_url

    @property
    def name(self) -> str:
        return "get_weather"

    @property
    def description(self) -> str:
        return "Get current weather and forecast for a destination city."

    @property
    def parameters_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "City name"},
                "days": {
                    "type": "integer",
                    "description": "Forecast days (1-10)",
                    "default": 3,
                },
            },
            "required": ["city"],
        }

    def execute(self, city: str, days: int = 3) -> dict:
        days = max(1, min(days, 10))
        try:
            resp = requests.get(
                f"{self._base_url}/forecast.json",
                params={"key": self._api_key, "q": city, "days": days, "lang": "zh"},
                timeout=10,
            )
            resp.raise_for_status()
            data = resp.json()

            location = data.get("location", {})
            current = data.get("current", {})
            forecast_days = data.get("forecast", {}).get("forecastday", [])

            return {
                "city": location.get("name", city),
                "country": location.get("country", ""),
                "current": {
                    "temp_c": current.get("temp_c"),
                    "condition": current.get("condition", {}).get("text", ""),
                    "humidity": current.get("humidity"),
                    "wind_kph": current.get("wind_kph"),
                },
                "forecast": [
                    {
                        "date": day.get("date"),
                        "max_temp_c": day.get("day", {}).get("maxtemp_c"),
                        "min_temp_c": day.get("day", {}).get("mintemp_c"),
                        "condition": day.get("day", {}).get("condition", {}).get("text", ""),
                        "rain_chance": day.get("day", {}).get("daily_chance_of_rain"),
                    }
                    for day in forecast_days
                ],
            }
        except Exception as e:
            return {"error": f"Weather API call failed: {e}"}
