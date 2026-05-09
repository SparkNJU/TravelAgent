from pydantic import BaseModel


class AgentChatRequest(BaseModel):
    query: str
    user_id: int = 1
    mode: str = "agent"  # "plan" or "agent"
    generate_plan_first: bool = True
    model: str | None = None
    temperature: float | None = None
    file_name: str | None = None
    file_base64: str | None = None
    file_mime_type: str | None = None
