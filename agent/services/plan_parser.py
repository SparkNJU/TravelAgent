import json
import uuid
import logging
from services.llm_service import LLMService
from services.amap_client import AMapClient, get_coordinates

logger = logging.getLogger(__name__)

def extract_plan_from_markdown(llm: LLMService, markdown_text: str, amap_client: AMapClient) -> dict:
    """
    Parses a markdown travel itinerary into a structured travel plan JSON.
    Then completes coordinates and injects temporary UUIDs for frontend usage.
    """
    prompt = f"""
请分析以下旅行规划的 Markdown 文本，并将其转换为结构化的 JSON 格式。
输出的 JSON 必须符合以下格式且不包含任何 Markdown 标记（例如 ```json 等，直接返回 JSON 串即可）：
{{
  "title": "旅行计划标题",
  "destination": "目的地城市名称（如 南京/杭州/北京）",
  "days": 3,
  "activities": [
    {{
      "dayNumber": 1,
      "activityTime": "具体时间或时间段，如 09:00 - 10:30，如无明确时间则写 全天",
      "locationName": "景点/地点名称，例如 夫子庙（必须是具体明确的地点，不要包含多余描述）",
      "description": "在该地点的具体活动描述",
      "tips": "游玩建议或备注，如果没有则写为 空或无",
      "cost": 0.0
    }}
  ]
}}

Markdown 文本如下：
{markdown_text}
"""
    messages = [{"role": "user", "content": prompt}]
    
    result = None
    # 1. Try calling via chat_json first (if model supports it)
    try:
        schema = {
            "name": "travel_plan",
            "strict": True,
            "schema": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "destination": {"type": "string"},
                    "days": {"type": "integer"},
                    "activities": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "dayNumber": {"type": "integer"},
                                "activityTime": {"type": "string"},
                                "locationName": {"type": "string"},
                                "description": {"type": "string"},
                                "tips": {"type": "string"},
                                "cost": {"type": "number"}
                            },
                            "required": ["dayNumber", "activityTime", "locationName", "description", "tips", "cost"],
                            "additionalProperties": False
                        }
                    }
                },
                "required": ["title", "destination", "days", "activities"],
                "additionalProperties": False
            }
        }
        result = llm.chat_json(messages, schema)
    except Exception as e:
        logger.warning(f"chat_json failed, falling back to chat: {e}")
        
    # 2. Fallback to standard chat and parse JSON from string
    if not result:
        try:
            content = llm.chat(messages, temperature=0.1)
            # Find JSON block
            if "```json" in content:
                content = content.split("```json")[1].split("```")[0]
            elif "```" in content:
                content = content.split("```")[1].split("```")[0]
            content = content.strip()
            result = json.loads(content)
        except Exception as e:
            logger.error(f"Failed to parse travel plan JSON from LLM output: {e}")
            result = {
                "title": "AI 规划行程",
                "destination": "未知",
                "days": 1,
                "activities": []
            }
            
    # 3. Geocode landmarks & Inject temporary UUIDs
    destination = result.get("destination") or "未知"
    activities = result.get("activities") or []
    for act in activities:
        # Inject temp uuid for frontend key tracking
        act["id"] = uuid.uuid4().hex
        
        # Geocode
        loc = act.get("locationName", "")
        lat, lng = get_coordinates(destination, loc, amap_client)
        act["latitude"] = lat
        act["longitude"] = lng
        
    result["activities"] = activities
    return result
