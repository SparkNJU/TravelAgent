from collections.abc import Iterator
import json

from app.services.tool_service import ToolService


class ChatService:
    def __init__(self) -> None:
        self.tool_service = ToolService()

    def _compose_answer(self, question: str, steps: list[dict]) -> str:
        city = "目的地"
        spots: list[str] = []
        transport = "地铁+步行"
        budget = "人均 300-600 元/天"
        weather_tip = "关注实时天气"

        for step in steps:
            if step.get("tool") == "city_detect":
                city = step.get("output", {}).get("city", city)
            elif step.get("tool") == "spot_search":
                spots = step.get("output", {}).get("spots", [])
            elif step.get("tool") == "travel_rules":
                output = step.get("output", {})
                transport = output.get("transport", transport)
                budget = output.get("budget", budget)
                weather_tip = output.get("weather", weather_tip)

        spots_text = "、".join(spots[:3]) if spots else "城市地标、特色街区"

        return (
            f"根据你的需求“{question}”，建议按 {city} 主题路线规划。\n"
            f"行程建议：优先安排 {spots_text}，并按早中晚拆分节奏。\n"
            f"交通建议：{transport}。\n"
            f"预算建议：{budget}。\n"
            f"天气提示：{weather_tip}。"
        )

    def stream_answer(self, session_id: str, question: str) -> Iterator[str]:
        """Stream deterministic answer with machine-readable tool trace."""
        _ = session_id
        steps = self.tool_service.get_tool_steps(question)
        yield "[TOOL_TRACE_JSON]" + json.dumps(steps, ensure_ascii=False)

        answer = self._compose_answer(question, steps)
        for line in answer.split("\n"):
            if line.strip():
                yield line
