from pydantic import BaseModel
from typing import List, Optional
from enum import Enum

class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"
    TOOL = "tool"

    # For compatibility, parse 'agent' to 'assistant'
    @classmethod
    def _missing_(cls, value):
        if value == "agent":
            return cls.ASSISTANT
        return super()._missing_(value)

class ChatMessage(BaseModel):
    role: MessageRole
    content: str

class AgentChatRequest(BaseModel):
    query: str
    user_id: int = 1
    mode: str = "agent"  # "plan", "agent", or "reflection"
    generate_plan_first: bool = False
    arena: bool = False
    model: str | None = None
    temperature: float | None = None
    file_name: str | None = None
    file_base64: str | None = None
    file_mime_type: str | None = None
    chat_history: List[ChatMessage] = []
    force_compress: bool = False
    web_search_enabled: bool = True
    knowledge_search_enabled: bool = True


class ParsePlanRequest(BaseModel):
    markdown: str
    destination: Optional[str] = None


class CompressRequest(BaseModel):
    chat_history: List[ChatMessage] = []
    keep_last: int | None = None
