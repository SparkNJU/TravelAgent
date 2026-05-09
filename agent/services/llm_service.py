"""Standalone LLM call service wrapping OpenAI-compatible API."""

from __future__ import annotations

import json
import os
from typing import Generator

from openai import OpenAI


class LLMService:
    def __init__(
        self,
        base_url: str,
        api_key_env: str,
        model: str,
        temperature: float = 0.7,
        max_tokens: int = 4096,
    ) -> None:
        api_key = os.getenv(api_key_env, "")
        if not api_key:
            raise RuntimeError(f"Environment variable {api_key_env} not set")
        self._client = OpenAI(api_key=api_key, base_url=base_url)
        self._model = model
        self._temperature = temperature
        self._max_tokens = max_tokens

    @property
    def client(self) -> OpenAI:
        return self._client

    @property
    def model(self) -> str:
        return self._model

    @property
    def temperature(self) -> float:
        return self._temperature

    def chat(self, messages: list[dict], temperature: float | None = None) -> str:
        resp = self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            temperature=temperature if temperature is not None else self._temperature,
            max_tokens=self._max_tokens,
        )
        return resp.choices[0].message.content or ""

    def chat_stream(
        self, messages: list[dict], temperature: float | None = None
    ) -> Generator[str, None, None]:
        completion = self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            temperature=temperature if temperature is not None else self._temperature,
            max_tokens=self._max_tokens,
            stream=True,
            stream_options={"include_usage": True},
        )
        for chunk in completion:
            yield chunk.model_dump_json()

    def chat_json(self, messages: list[dict], json_schema: dict) -> dict:
        resp = self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            temperature=0.1,
            response_format={"type": "json_schema", "json_schema": json_schema},
        )
        raw = resp.choices[0].message.content or "{}"
        return json.loads(raw)

    def chat_with_tools(
        self, messages: list[dict], tools: list[dict], temperature: float | None = None
    ):
        resp = self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            tools=tools,
            tool_choice="auto",
            temperature=temperature if temperature is not None else self._temperature,
            max_tokens=self._max_tokens,
        )
        return resp.choices[0].message
