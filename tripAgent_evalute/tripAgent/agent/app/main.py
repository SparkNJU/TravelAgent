from fastapi import FastAPI

from app.api.routes import router
from app.core.config import settings

app = FastAPI(title="TripAgent Service", version="0.1.0")
app.include_router(router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "agent", "env": settings.app_env}
