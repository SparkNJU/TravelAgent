from typing import Any


class ToolService:
    """Simple local tools used by the demo agent service."""

    def _detect_city(self, question: str) -> str:
        city_candidates = ["北京", "上海", "广州", "深圳", "成都", "杭州", "重庆", "西安"]
        for city in city_candidates:
            if city in question:
                return city
        return "目的地"

    def _recommend_spots(self, city: str) -> list[str]:
        presets = {
            "北京": ["故宫", "颐和园", "前门大街"],
            "上海": ["外滩", "豫园", "徐汇滨江"],
            "广州": ["沙面", "永庆坊", "珠江夜游"],
            "成都": ["宽窄巷子", "杜甫草堂", "锦江夜游"],
        }
        return presets.get(city, ["城市地标", "博物馆", "特色街区"])

    def get_tool_steps(self, question: str) -> list[dict[str, Any]]:
        city = self._detect_city(question)
        spots = self._recommend_spots(city)
        weather_tip = "如遇降雨，优先安排室内馆点并预留机动时间"

        return [
            {
                "tool": "city_detect",
                "input": question,
                "output": {"city": city},
            },
            {
                "tool": "spot_search",
                "input": {"city": city, "query": question},
                "output": {"spots": spots},
            },
            {
                "tool": "travel_rules",
                "input": {"city": city},
                "output": {
                    "transport": "优先地铁+步行，跨区用网约车",
                    "budget": "人均 300-600 元/天",
                    "weather": weather_tip,
                },
            },
        ]
