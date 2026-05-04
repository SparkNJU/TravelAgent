from fastapi import APIRouter, Query
from fastapi.responses import StreamingResponse

from app.services.chat_service import ChatService

router = APIRouter(prefix="/agent", tags=["agent"])
chat_service = ChatService()


@router.get("/chat/stream")
def stream_chat(
    sessionId: str = Query(..., min_length=1),
    question: str = Query(..., min_length=1),
) -> StreamingResponse:
    def event_stream():
        for chunk in chat_service.stream_answer(sessionId, question):
            yield f"data: {chunk}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
