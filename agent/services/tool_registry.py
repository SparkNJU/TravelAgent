"""Tool interface, registry, and built-in tool implementations."""

from __future__ import annotations

import json
from abc import ABC, abstractmethod
from typing import Any

from .file_parser import parse_uploaded_file
from .serper_client import SerperClient


class Tool(ABC):
    @property
    @abstractmethod
    def name(self) -> str: ...

    @property
    @abstractmethod
    def description(self) -> str: ...

    @property
    @abstractmethod
    def parameters_schema(self) -> dict: ...

    @abstractmethod
    def execute(self, **kwargs) -> Any: ...


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, Tool] = {}

    def register(self, tool: Tool) -> None:
        self._tools[tool.name] = tool

    def get(self, name: str) -> Tool | None:
        return self._tools.get(name)

    def list_tools(self) -> list[dict]:
        return [
            {
                "type": "function",
                "function": {
                    "name": t.name,
                    "description": t.description,
                    "parameters": t.parameters_schema,
                },
            }
            for t in self._tools.values()
        ]

    def call(self, name: str, arguments: dict) -> Any:
        tool = self._tools.get(name)
        if not tool:
            raise ValueError(f"Unknown tool: {name}")
        return tool.execute(**arguments)


class WebSearchTool(Tool):
    def __init__(self, serper: SerperClient) -> None:
        self._serper = serper

    @property
    def name(self) -> str:
        return "web_search"

    @property
    def description(self) -> str:
        return "Search the web for travel information, attractions, food, and transportation tips."

    @property
    def parameters_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Search query"},
                "num": {
                    "type": "integer",
                    "description": "Number of results",
                    "default": 5,
                },
            },
            "required": ["query"],
        }

    def execute(self, query: str, num: int = 5) -> list[dict]:
        return self._serper.search(query, num=num)


class FileParserTool(Tool):
    @property
    def name(self) -> str:
        return "parse_file"

    @property
    def description(self) -> str:
        return "Parse an uploaded file (txt/pdf/docx) and extract its text content."

    @property
    def parameters_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "file_name": {"type": "string", "description": "Original file name"},
                "file_base64": {
                    "type": "string",
                    "description": "Base64-encoded file content",
                },
            },
            "required": ["file_name", "file_base64"],
        }

    def execute(self, file_name: str, file_base64: str) -> str:
        return parse_uploaded_file(file_name, file_base64) or ""


class UserConfirmTool(Tool):
    @property
    def name(self) -> str:
        return "ask_user"

    @property
    def description(self) -> str:
        return (
            "Ask the user clarifying questions before continuing. "
            "Each question MUST have at least 2 predefined options for the user to pick from. "
            "The user can also type a custom answer. "
            "Use this when you need user preferences, budget, dates, or "
            "other details that affect the travel plan."
        )

    @property
    def parameters_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "message": {
                    "type": "string",
                    "description": "A brief message to the user explaining why you need this info",
                },
                "questions": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "question": {"type": "string", "description": "The question to ask"},
                            "options": {
                                "type": "array",
                                "items": {"type": "string"},
                                "description": "At least 2 predefined options for the user to choose from.",
                                "minItems": 2,
                            },
                        },
                        "required": ["question", "options"],
                    },
                    "minItems": 1,
                },
            },
            "required": ["message", "questions"],
        }

    def execute(self, message: str, questions: list) -> str:
        if not questions or not isinstance(questions, list):
            questions = [{"question": message or "请提供更多信息", "options": ["选项1", "选项2"]}]
        for q in questions:
            if not isinstance(q, dict):
                continue
            opts = q.get("options", [])
            if not isinstance(opts, list) or len(opts) < 2:
                q["options"] = opts[:2] if isinstance(opts, list) and len(opts) >= 2 else ["选项1", "选项2"]
        return json.dumps({
            "status": "waiting_for_user",
            "message": message,
            "questions": questions,
        }, ensure_ascii=False)


class SuggestQuestionsTool(Tool):
    def __init__(self, llm=None) -> None:
        self._llm = llm

    @property
    def name(self) -> str:
        return "suggest_questions"

    @property
    def description(self) -> str:
        return (
            "Generate 3 suggested follow-up questions the user might want to ask next. "
            "Call this alongside the finish tool when you are ready to complete the travel plan."
        )

    @property
    def parameters_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "context": {
                    "type": "string",
                    "description": "Brief summary of the conversation so far",
                },
            },
            "required": ["context"],
        }

    def execute(self, context: str) -> list[str]:
        if not self._llm:
            return []
        prompt = (
            "Based on the following travel planning conversation, suggest 3 short follow-up "
            "questions the user might want to ask next. Return ONLY a JSON array of 3 strings. "
            "Each question should be under 30 characters in Chinese.\n\n"
            f"Conversation context:\n{context[:1500]}\n\n"
            'Return format: ["问题1", "问题2", "问题3"]'
        )
        try:
            import re
            result = self._llm.chat(
                [{"role": "user", "content": prompt}],
                temperature=0.7,
            )
            match = re.search(r'\[.*?\]', result, re.DOTALL)
            if match:
                return json.loads(match.group())
        except Exception:
            pass
        return []


class FinishTool(Tool):
    @property
    def name(self) -> str:
        return "finish"

    @property
    def description(self) -> str:
        return (
            "Call this tool when you have completed the travel plan and are ready to finish. "
            "The 'answer' parameter should contain the complete travel plan in Markdown format. "
            "You may call other tools (like suggest_questions) in the same turn."
        )

    @property
    def parameters_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "answer": {
                    "type": "string",
                    "description": "The complete travel plan in Markdown format to present to the user",
                },
            },
            "required": ["answer"],
        }

    def execute(self, answer: str) -> str:
        return json.dumps(
            {"status": "finished", "answer": answer}, ensure_ascii=False
        )
