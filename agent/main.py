from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI

from models import TripPlanRequest
from services.file_parser import parse_uploaded_file
from services.planner import TripPlanner
from services.serper_client import SerperClient

app = FastAPI(title="Travel Assistant Agent", version="1.0.0")

_serper = SerperClient()
_planner = TripPlanner(_serper)


@app.get("/health")
def health() -> dict:
    return {"ok": True, "serper_enabled": _serper.enabled}


@app.post("/api/trip/plan")
def trip_plan(request: TripPlanRequest) -> dict:
    file_text = parse_uploaded_file(request.file_name, request.file_base64)
    file_summary = file_text[:600] if file_text else ""
    return _planner.generate(query=request.query, file_summary=file_summary)
