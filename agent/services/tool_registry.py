"""Tool interface, registry, and built-in tool implementations."""

from __future__ import annotations

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
