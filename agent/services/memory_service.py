from __future__ import annotations

import json
from dataclasses import dataclass

import requests

from config import config
from .llm_service import LLMService


_MEMORY_SCHEMA = {
    "name": "agent_memory_extraction",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "user_facts": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "key": {"type": "string"},
                        "value": {"type": "string"},
                        "evidence": {"type": "string"},
                        "confidence": {"type": "number"},
                    },
                    "required": ["key", "value", "evidence", "confidence"],
                    "additionalProperties": False,
                },
            },
            "conversation_summary": {"type": "string"},
            "public_knowledge": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "knowledgeKey": {"type": "string"},
                        "knowledgeTitle": {"type": "string"},
                        "knowledgeContent": {"type": "string"},
                        "knowledgeScope": {"type": "string"},
                        "confidenceScore": {"type": "number"},
                        "evidence": {"type": "string"},
                    },
                    "required": [
                        "knowledgeKey",
                        "knowledgeTitle",
                        "knowledgeContent",
                        "knowledgeScope",
                        "confidenceScore",
                        "evidence",
                    ],
                    "additionalProperties": False,
                },
            },
            "memory_markdown": {"type": "string"},
        },
        "required": ["user_facts", "conversation_summary", "public_knowledge", "memory_markdown"],
        "additionalProperties": False,
    },
}


@dataclass
class MemoryExtractionResult:
    user_facts: list[dict]
    conversation_summary: str
    public_knowledge: list[dict]
    memory_markdown: str


class MemoryService:
    def __init__(self, llm: LLMService, backend_base_url: str | None = None) -> None:
        self._llm = llm
        self._backend_base_url = (backend_base_url or config.backend.base_url).rstrip("/")

    def fetch_latest(self, user_id: int) -> dict:
        try:
            response = requests.get(
                f"{self._backend_base_url}/api/agent/memory/{user_id}",
                timeout=10,
            )
            response.raise_for_status()
            try:
                payload = response.json()
            except Exception as e:
                raise RuntimeError(f"Invalid JSON from memory GET: {e}; raw={response.text}")
            if isinstance(payload, dict):
                data = payload.get("data")
                return data if isinstance(data, dict) else {}
            return {}
        except requests.RequestException as e:
            resp_text = None
            if hasattr(e, 'response') and e.response is not None:
                resp_text = e.response.text
            raise RuntimeError(f"HTTP error fetching latest memory: {e}; response_text={resp_text}")

    def extract(self, query: str, answer: str, chat_history: list[dict], user_name: str | None = None) -> MemoryExtractionResult:
        context_text = self._build_context(query, answer, chat_history, user_name)
        prompt = (
            "你是旅行助手的记忆抽取器。你的任务是从一次对话中抽取长期记忆。\n"
            "必须输出严格合法 JSON。禁止输出任何解释文本。\n\n"
            
            "禁止输出 markdown、解释、自然语言。\n"
            "禁止把 user_facts 写成字符串数组。\n"
            "user_facts 每一项必须是对象。\n\n"

            "正确示例：\n"
            "{\n"
            '  "user_facts":[\n'
            "    {\n"
            '      "key":"user_name",\n'
            '      "value":"小米",\n'
            '      "evidence":"用户说我叫小米",\n'
            '      "confidence":0.95\n'
            "    }\n"
            "  ],\n"
            '  "conversation_summary":"...",\n'
            '  "public_knowledge":[],\n'
            '  "memory_markdown":"..."\n'
            "}\n\n"

            "错误示例：\n"
            '["用户姓名为小米"]\n'
            "↑ 禁止这种格式。\n\n"

            "抽取原则：\n"
            "1. 只保留稳定且可复用的事实，例如用户名字、默认同行人数、常问目的地、偏好、禁忌。\n"
            "2. 对话摘要要简洁，突出本轮对话的核心意图与结论。\n"
            "3. 公共知识只能写入对其他用户也有复用价值的旅行知识。\n"
            "4. 如果没有新事实，不要编造；user_facts 可以为空数组。\n"
            "5. memory_markdown 必须是可直接写入 AGENT.md 的 Markdown。\n\n"
            f"对话上下文：\n{context_text[:6000]}"
        )
        result = self._llm.chat_json([
            {"role": "system", "content": "你是一个严格的 JSON 记忆抽取器。"},
            {"role": "user", "content": prompt},
        ], _MEMORY_SCHEMA)

        normalized_facts = self._normalize_user_facts(result.get("user_facts", []))

        return MemoryExtractionResult(
            user_facts=normalized_facts,
            conversation_summary=result.get("conversation_summary", ""),
            public_knowledge=result.get("public_knowledge", []),
            memory_markdown=result.get("memory_markdown", ""),
        )

    def sync(
        self,
        user_id: int,
        query: str,
        answer: str,
        chat_history: list[dict],
        model_version: str | None = None,
        source_conversation_id: int | None = None,
    ) -> dict:
        user_name = self._extract_user_name(chat_history, query)
        extraction = self.extract(query, answer, chat_history, user_name=user_name)
        request_body = {
            "userId": user_id,
            "sourceConversationId": source_conversation_id,
            "triggerQuery": query,
            "modelVersion": model_version or self._llm.model,
            "tokenInput": self._estimate_tokens(query, chat_history),
            "tokenOutput": self._estimate_tokens(answer, []),
            "userFactsJson": json.dumps(extraction.user_facts, ensure_ascii=False),
            "memoryMarkdown": extraction.memory_markdown,
            "conversationSummary": extraction.conversation_summary,
            "publicKnowledgeJson": json.dumps(extraction.public_knowledge, ensure_ascii=False),
        }

        response = requests.post(
            f"{self._backend_base_url}/api/agent/memory/sync",
            json=request_body,
            timeout=20,
        )
        try:
            response.raise_for_status()
        except requests.RequestException as e:
            resp_text = None
            if hasattr(response, 'text'):
                resp_text = response.text
            raise RuntimeError(f"Memory sync HTTP error: {e}; response_text={resp_text}")
        try:
            return response.json()
        except Exception as e:
            raise RuntimeError(f"Invalid JSON from memory sync response: {e}; raw={response.text}")

    def _build_context(self, query: str, answer: str, chat_history: list[dict], user_name: str | None) -> str:
        lines = []
        if user_name:
            lines.append(f"已知用户名字: {user_name}")
        if chat_history:
            lines.append("历史对话:")
            for item in chat_history[-8:]:
                role = item.get("role", "unknown")
                content = item.get("content", "")
                lines.append(f"- {role}: {content}")
        lines.append(f"本轮用户问题: {query}")
        lines.append(f"本轮助手回答: {answer}")
        return "\n".join(lines)

    def _extract_user_name(self, chat_history: list[dict], query: str) -> str | None:
        for item in reversed(chat_history):
            content = str(item.get("content", ""))
            for marker in ("我叫", "我是", "名字叫"):
                if marker in content:
                    tail = content.split(marker, 1)[1].strip()
                    if tail:
                        return tail[:20].split(" ")[0].split("，")[0].split("。")[0]
        for marker in ("我叫", "我是", "名字叫"):
            if marker in query:
                tail = query.split(marker, 1)[1].strip()
                if tail:
                    return tail[:20].split(" ")[0].split("，")[0].split("。")[0]
        return None

    def _estimate_tokens(self, text: str, extra_messages: list[dict]) -> int:
        total_chars = len(text or "") + sum(len(str(item.get("content", ""))) for item in extra_messages)
        return max(1, total_chars // 4)

    def _normalize_user_facts(self, facts: list) -> list[dict]:
        normalized: list[dict] = []

        for item in facts:
            if isinstance(item, str):
                text = item.strip()

                m = re.search(r"用户姓名(?:为|是)?(.+)", text)
                if m:
                    normalized.append({
                        "key": "user_name",
                        "value": m.group(1).strip(),
                        "evidence": text,
                        "confidence": 0.9,
                    })
                    continue

                m = re.search(r"默认(?:出游)?人数(?:为|是)?(\d+)", text)
                if m:
                    normalized.append({
                        "key": "travel_party_size",
                        "value": m.group(1),
                        "evidence": text,
                        "confidence": 0.8,
                    })
                    continue

                m = re.search(r"(?:常问|喜欢|关注)(.+)")
                if m:
                    normalized.append({
                        "key": "frequent_destination",
                        "value": m.group(1).strip(),
                        "evidence": text,
                        "confidence": 0.7,
                    })
                    continue

                # fallback
                normalized.append({
                    "key": "general_fact",
                    "value": text,
                    "evidence": text,
                    "confidence": 0.6,
                })
                continue

            if isinstance(item, dict):
                value = str(item.get("value", "")).strip()

                if not value:
                    continue

                normalized.append({
                    "key": str(item.get("key", "fact")),
                    "value": value,
                    "evidence": str(item.get("evidence", "")),
                    "confidence": float(item.get("confidence", 0.6)),
                })

        return normalized