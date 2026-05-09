from __future__ import annotations

import json
import os

from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI
from fastapi.responses import StreamingResponse

from config import config
from models import AgentChatRequest
from services.file_parser import parse_uploaded_file
from services.llm_service import LLMService
from services.planner import MetaPlanner
from services.react_agent import ReActAgent
from services.reflection_agent import ReflectionAgent
from services.serper_client import SerperClient
from services.sse_events import sse_event, SSE_DONE
from services.tool_registry import FileParserTool, ToolRegistry, WebSearchTool

app = FastAPI(title="Travel Assistant Agent", version="2.0.0")

# --- Initialize services ---

_llm = LLMService(
    base_url=config.llm.base_url,
    api_key_env=config.llm.api_key_env,
    model=config.llm.chat_model,
    temperature=config.llm.temperature,
    max_tokens=config.llm.max_tokens,
)

_serper = SerperClient()

_tool_registry = ToolRegistry()
_tool_registry.register(WebSearchTool(_serper))
_tool_registry.register(FileParserTool())

_planner = MetaPlanner(_llm)
_agent = ReActAgent(
    llm=_llm,
    tool_registry=_tool_registry,
    max_iterations=config.agent.max_iterations,
    max_retries=config.agent.self_correction_retries,
)

_reflection_agent = ReflectionAgent(
    llm=_llm,
    react_agent=_agent,
)


@app.get("/health")
def health() -> dict:
    return {
        "ok": True,
        "serper_enabled": _serper.enabled,
        "tools_registered": [
            t["function"]["name"] for t in _tool_registry.list_tools()
        ],
    }


@app.post("/api/agent/chat")
async def agent_chat(request: AgentChatRequest) -> StreamingResponse:
    # Per-request model/temperature override
    llm = _llm
    planner = _planner
    agent = _agent
    reflection_agent = _reflection_agent
    if request.model or request.temperature is not None:
        llm = LLMService(
            base_url=config.llm.base_url,
            api_key_env=config.llm.api_key_env,
            model=request.model or config.llm.chat_model,
            temperature=request.temperature if request.temperature is not None else config.llm.temperature,
            max_tokens=config.llm.max_tokens,
        )
        planner = MetaPlanner(llm)
        agent = ReActAgent(
            llm=llm,
            tool_registry=_tool_registry,
            max_iterations=config.agent.max_iterations,
            max_retries=config.agent.self_correction_retries,
        )
        reflection_agent = ReflectionAgent(llm=llm, react_agent=agent)

    def event_stream():
        try:
            file_summary = ""
            if request.file_name and request.file_base64:
                file_text = parse_uploaded_file(request.file_name, request.file_base64)
                file_summary = file_text[:600] if file_text else ""

            if request.mode == "plan":
                for event_json in planner.generate_plan(request.query, file_summary):
                    yield f"data: {event_json}\n\n"
            elif request.mode == "reflection":
                execution_plan = ""
                if request.generate_plan_first:
                    yield sse_event("plan", "Generating execution plan...", {})
                    for event_json in planner.generate_plan(
                        request.query, file_summary
                    ):
                        chunk = json.loads(event_json)
                        execution_plan += chunk.get("content", "")
                        yield f"data: {event_json}\n\n"

                for event_json in reflection_agent.run(
                    request.query, file_summary, execution_plan
                ):
                    yield f"data: {event_json}\n\n"
            else:
                execution_plan = ""
                if request.generate_plan_first:
                    yield sse_event("plan", "Generating execution plan...", {})
                    for event_json in planner.generate_plan(
                        request.query, file_summary
                    ):
                        chunk = json.loads(event_json)
                        execution_plan += chunk.get("content", "")
                        yield f"data: {event_json}\n\n"

                for event_json in agent.run(
                    request.query, file_summary, execution_plan
                ):
                    yield f"data: {event_json}\n\n"

            yield SSE_DONE
        except Exception as e:
            yield sse_event("error", str(e), {})
            yield SSE_DONE

    return StreamingResponse(event_stream(), media_type="text/event-stream; charset=utf-8")
