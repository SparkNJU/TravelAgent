from __future__ import annotations

import json
import os

from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI
from fastapi.responses import StreamingResponse

from config import config
from models import AgentChatRequest, ParsePlanRequest, CompressRequest
from services.file_parser import parse_uploaded_file
from services.llm_service import LLMService
from services.memory_service import MemoryService
from services.planner import MetaPlanner
from services.react_agent import ReActAgent
from services.reflection_agent import ReflectionAgent
from services.serper_client import SerperClient
from services.amap_client import AMapClient
from services.plan_parser import extract_plan_from_markdown
from services.sse_events import sse_event, SSE_DONE
from services.tool_registry import (
    ActivateSkillTool,
    CreateSkillTool,
    FileParserTool,
    FinishTool,
    KnowledgeSearchTool,
    SuggestQuestionsTool,
    ToolRegistry,
    UserConfirmTool,
    WebSearchTool,
)
from knowledge.client import HttpKnowledgeClient, InProcessKnowledgeClient
from knowledge.provider import get_knowledge_service
from knowledge.router import router as knowledge_router

app = FastAPI(title="Travel Assistant Agent", version="2.0.0")
app.include_router(knowledge_router)

# --- Initialize services ---

_llm = LLMService(
    base_url=config.llm.base_url,
    api_key_env=config.llm.api_key_env,
    model=config.llm.chat_model,
    temperature=config.llm.temperature,
    max_tokens=config.llm.max_tokens,
)

_serper = SerperClient()
_amap_client = AMapClient()

if config.knowledge.mode == "http":
    _knowledge_client = HttpKnowledgeClient(config.knowledge.base_url)
else:
    _knowledge_client = InProcessKnowledgeClient(get_knowledge_service)

_tool_registry = ToolRegistry()
_tool_registry.register(WebSearchTool(_serper))
if config.knowledge.enabled:
    _tool_registry.register(KnowledgeSearchTool(_knowledge_client, default_top_k=config.knowledge.top_k))
_tool_registry.register(FileParserTool())
_tool_registry.register(UserConfirmTool())
_tool_registry.register(SuggestQuestionsTool(_llm))
_tool_registry.register(FinishTool())
_tool_registry.register(ActivateSkillTool(user_id=1))
_tool_registry.register(CreateSkillTool(user_id=1))


def build_tool_registry(
    llm: LLMService,
    allow_user_confirm: bool = True,
    allow_suggestions: bool = True,
    user_id: int = 1,
    web_search_enabled: bool = True,
    knowledge_search_enabled: bool = True,
) -> ToolRegistry:
    registry = ToolRegistry()
    if web_search_enabled:
        registry.register(WebSearchTool(_serper))
    if knowledge_search_enabled and config.knowledge.enabled:
        registry.register(KnowledgeSearchTool(_knowledge_client, default_top_k=config.knowledge.top_k))
    registry.register(FileParserTool())
    if allow_user_confirm:
        registry.register(UserConfirmTool())
    if allow_suggestions:
        registry.register(SuggestQuestionsTool(llm))
    registry.register(FinishTool())
    registry.register(ActivateSkillTool(user_id=user_id))
    registry.register(CreateSkillTool(user_id=user_id))
    return registry

_planner = MetaPlanner(_llm)
_memory_service = MemoryService(_llm, backend_base_url=config.backend.base_url)
_agent = ReActAgent(
    llm=_llm,
    tool_registry=_tool_registry,
    max_iterations=config.agent.max_iterations,
    max_retries=config.agent.self_correction_retries,
    max_context_tokens=10000,      
    compress_threshold=0.80,       
    compress_keep_last=6,         
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
    # Build per-request scoped services
    llm = _llm
    allow_user_confirm = not request.arena
    allow_suggestions = not request.arena
    if request.model or request.temperature is not None:
        llm = LLMService(
            base_url=config.llm.base_url,
            api_key_env=config.llm.api_key_env,
            model=request.model or config.llm.chat_model,
            temperature=request.temperature if request.temperature is not None else config.llm.temperature,
            max_tokens=config.llm.max_tokens,
        )
    planner = MetaPlanner(llm)
    tool_registry = build_tool_registry(
        llm, allow_user_confirm, allow_suggestions,
        user_id=request.user_id,
        web_search_enabled=request.web_search_enabled,
        knowledge_search_enabled=request.knowledge_search_enabled,
    )
    agent = ReActAgent(
        llm=llm,
        tool_registry=tool_registry,
        max_iterations=config.agent.max_iterations,
        max_retries=config.agent.self_correction_retries,
        max_context_tokens=config.agent.max_context_tokens,
        compress_threshold=config.agent.compress_threshold,
        compress_keep_last=config.agent.compress_keep_last,
        enable_plan_checker=False,  # agent 模式不启用 plan checker
    )
    reflection_react_agent = ReActAgent(
        llm=llm,
        tool_registry=tool_registry,
        max_iterations=config.agent.max_iterations,
        max_retries=config.agent.self_correction_retries,
        max_context_tokens=config.agent.max_context_tokens,
        compress_threshold=config.agent.compress_threshold,
        compress_keep_last=config.agent.compress_keep_last,
        enable_plan_checker=True,  # reflection 模式启用 plan checker
    )
    reflection_agent = ReflectionAgent(llm=llm, react_agent=reflection_react_agent)

    def event_stream():
        try:
            file_summary = ""
            if request.file_name and request.file_base64:
                file_text = parse_uploaded_file(request.file_name, request.file_base64)
                file_summary = file_text[:600] if file_text else ""

            history = [{"role": msg.role.value, "content": msg.content} for msg in request.chat_history]
            user_memory_markdown = ""
            try:
                latest_memory = _memory_service.fetch_latest(request.user_id)
                memory_obj = latest_memory.get("memory") if isinstance(latest_memory, dict) else None
                if isinstance(memory_obj, dict):
                    user_memory_markdown = str(memory_obj.get("memoryMarkdown") or "")
                elif memory_obj is not None:
                    user_memory_markdown = str(getattr(memory_obj, "memoryMarkdown", "") or "")
            except Exception:
                user_memory_markdown = ""

            answer_text = ""
            is_ask_user = False

            if request.mode == "plan":
                plan_text = ""
                for event_json in planner.generate_plan(request.query, file_summary):
                    try:
                        chunk = json.loads(event_json)
                        plan_text += chunk.get("content", "")
                    except Exception:
                        pass
                    yield f"data: {event_json}\n\n"
                answer_text = plan_text
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
                    request.query,
                    file_summary,
                    execution_plan,
                    user_memory_markdown=user_memory_markdown,
                ):
                    chunk = json.loads(event_json)
                    if chunk.get("type") == "answer":
                        answer_text += chunk.get("content", "")
                    if chunk.get("type") == "ask_user":
                        is_ask_user = True
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
                    request.query,
                    file_summary,
                    execution_plan,
                    chat_history=history,
                    user_memory_markdown=user_memory_markdown,
                    arena_mode=request.arena,
                    user_id=request.user_id,
                    force_compress=request.force_compress,
                ):
                    chunk = json.loads(event_json)
                    if chunk.get("type") == "answer":
                        answer_text += chunk.get("content", "")
                    if chunk.get("type") == "ask_user":
                        is_ask_user = True
                    yield f"data: {event_json}\n\n"

            # Generate suggestions only if we have an answer and no ask_user
            if allow_suggestions and answer_text.strip() and not is_ask_user:
                suggest_tool = tool_registry.get("suggest_questions")
                if suggest_tool:
                    suggestions = suggest_tool.execute(
                        context=f"User: {request.query}\nAgent: {answer_text[:1500]}"
                    )
                    if suggestions:
                        yield sse_event("suggestions", "", {"questions": suggestions})

            if answer_text.strip() and not is_ask_user:
                try:
                    memory_result = _memory_service.sync(
                        user_id=request.user_id,
                        query=request.query,
                        answer=answer_text,
                        chat_history=history,
                        model_version=llm.model,
                    )
                    yield sse_event("memory", "Memory synced", memory_result)
                except Exception as memory_error:
                    yield sse_event("memory", "Memory sync skipped", {"error": str(memory_error)})

            yield SSE_DONE
        except Exception as e:
            import traceback
            traceback.print_exc()
            yield sse_event("error", str(e), {})
            yield SSE_DONE

    return StreamingResponse(event_stream(), media_type="text/event-stream; charset=utf-8")

@app.post("/api/agent/parse-plan")
def parse_plan(request: ParsePlanRequest) -> dict:
    try:
        result = extract_plan_from_markdown(_llm, request.markdown, _amap_client)
        if request.destination and not result.get("destination"):
            result["destination"] = request.destination
        return {"code": 200, "data": result}
    except Exception as e:
        return {"code": 500, "message": str(e)}

@app.post("/api/agent/compress")
async def agent_compress(request: CompressRequest) -> dict:
    history = [
        {"role": msg.role.value, "content": msg.content}
        for msg in request.chat_history
    ]
    return _agent.compress_history(history, request.keep_last)
