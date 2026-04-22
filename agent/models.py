from pydantic import BaseModel


class TripPlanRequest(BaseModel):
    query: str
    user_id: int = 1
    file_name: str | None = None
    file_base64: str | None = None
    file_mime_type: str | None = None
