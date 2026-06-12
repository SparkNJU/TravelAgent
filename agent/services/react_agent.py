"""Agent mode: executes travel planning via ReAct (Think-Act-Observe) loop."""

from __future__ import annotations

import json
from typing import Any, Generator

from .llm_service import LLMService
from .tool_registry import ToolRegistry

_SYSTEM_PROMPT_TEMPLATE = """\
You are a travel planning assistant agent.

You help users create detailed travel itineraries.

CRITICAL: You MUST respond in Chinese (简体中文) at all times. Never use English.

Today's date is {current_date}. Use this as the reference date for all travel planning.

=========================
LONG-TERM USER MEMORY
=========================

{user_memory}

The long-term memory above contains durable facts about the user.

Rules for memory:
1. Treat remembered facts as true unless the user explicitly corrects them.
2. If the memory contains the user's name, preferences, travel habits, or commonly asked destinations, you SHOULD naturally use them.
3. Never claim you cannot remember previous information if memory exists.
4. Ignore invalid or contradictory memory.
5. If current user instructions conflict with memory, ALWAYS prioritize the newest instruction.

=========================
AVAILABLE TOOLS
=========================

{tools}

Follow the ReAct pattern:

1. THINK:
Analyze what information you need next.

2. ACT:
Call tools when needed.

3. OBSERVE:
Review tool results.

When travel planning is needed and you have gathered enough information:
- Output the complete travel plan
- Call `finish`
- You may call `suggest_questions`

For casual conversation, greetings, memory updates, or profile information:
- You may respond naturally without tool calls.

When you need to ask the user clarifying questions about their travel preferences:
- You MUST call the `ask_user` tool with the 'message' and 'questions' parameters
- Each question MUST include at least 2 predefined options
- Do NOT write questions as text in your response — always use ask_user

Important:
- Always search for up-to-date information
- Check weather forecasts
- Retry failed tools
- Structure answer day-by-day
- Use ask_user to clarify travel preferences when essential information is missing
- Only call finish when a travel-related task is complete
"""


class ReActAgent:
    """Executes travel planning via Think-Act-Observe loop with self-correction."""

    def __init__(
        self,
        llm: LLMService,
        tool_registry: ToolRegistry,
        max_iterations: int = 8,
        max_retries: int = 2,
        max_context_tokens: int = 12000,
        compress_threshold: float = 0.85,
        compress_keep_last: int = 6,
    ) -> None:
        self._llm = llm
        self._tools = tool_registry
        self._max_iterations = max_iterations
        self._max_retries = max_retries
        self._max_context_tokens = max_context_tokens
        self._compress_threshold = compress_threshold
        self._compress_keep_last = compress_keep_last

    def _sanitize_memory(self, memory_markdown: str) -> str:
        """Remove misleading memory and keep durable user facts."""
        if not memory_markdown:
            return ""
        BAD_PATTERNS = [
            "无法访问历史对话",
            "无法查看历史记录",
            "无法记住之前",
            "我是AI",
            "不能记忆",
            "无法访问用户信息",
            "我无法得知",
            "作为一个AI",
            "不知道你的身份",
        ]
        cleaned_lines = []

        for line in memory_markdown.splitlines():
            line = line.strip()
            if not line:
                cleaned_lines.append("")
                continue
            skip = False
            for pattern in BAD_PATTERNS:
                if pattern in line:
                    skip = True
                    break
            if not skip:
                cleaned_lines.append(line)
        return "\n".join(cleaned_lines).strip()
    
    def run(
        self,
        query: str,
        file_summary: str = "",
        execution_plan: str = "",
        chat_history: list[dict] | None = None,
        user_memory_markdown: str = "",
        arena_mode: bool = False,
        force_compress: bool = False,
        user_id: int = 1,
    ) -> Generator[str, None, None]:
        """Execute the ReAct loop. Yields SSE event JSON strings."""
        if chat_history is None:
            chat_history = []
            
        tool_descriptions = "\n".join(
            f"- {t['function']['name']}: {t['function']['description']}"
            for t in self._tools.list_tools()
        )
        cleaned_memory = self._sanitize_memory(
            user_memory_markdown
        )
        from datetime import date
        system_prompt = _SYSTEM_PROMPT_TEMPLATE.format(
            tools=tool_descriptions,
            user_memory=cleaned_memory or "(empty)",
            current_date=date.today().strftime("%Y年%m月%d日"),
        )

        # Level 1: Fetch active skills from backend and append to system prompt
        try:
            import os
            import requests
            backend_url = os.getenv("BACKEND_URL", "http://localhost:8080")
            res = requests.get(f"{backend_url}/api/skills/active", params={"userId": user_id}, timeout=3)
            if res.status_code == 200:
                skills_data = res.json().get("data", [])
                if skills_data and isinstance(skills_data, list):
                    active_skills_desc = "\n\nYou have access to specialized Skills. If the user request matches the domain of a Skill, you MUST first call the `activate_skill(skill_name)` tool to retrieve its detailed instructions and follow them carefully.\nAvailable Skills:\n"
                    for s in skills_data:
                        if s and isinstance(s, dict):
                            name = s.get("name")
                            desc = s.get("description")
                            if name and desc:
                                active_skills_desc += f"- {name}: {desc}\n"
                    system_prompt += active_skills_desc
        except Exception:
            pass
        # Level 2: Fetch active memories from backend and append to system prompt
        try:
            import os
            import requests
            backend_url = os.getenv("BACKEND_URL", "http://localhost:8080")
            res = requests.get(f"{backend_url}/api/memories/active", params={"userId": user_id}, timeout=3)
            if res.status_code == 200:
                memories_data = res.json().get("data", [])
                if memories_data and isinstance(memories_data, list):
                    active_memories_desc = "\n\nYou have access to the user's personal preferences and profile details (Personal Memories). You MUST strictly respect and satisfy all of these conditions during travel planning without asking the user about them:\nUser Personal Preferences/Memories:\n"
                    for m in memories_data:
                        if m and isinstance(m, dict):
                            content = m.get("content")
                            if content:
                                active_memories_desc += f"- {content}\n"
                    system_prompt += active_memories_desc
        except Exception:
            pass
        if arena_mode:
            system_prompt += (
                "\nArena mode: do not call ask_user or suggest_questions. "
                "Provide the full travel plan and call finish exactly once. "
                "Do NOT call finish with empty arguments or placeholder text like 'The answer is provided.'"
            )

        user_parts = [f"User request: {query}"]
        if file_summary:
            user_parts.append(f"\nUploaded file content:\n{file_summary}")
        if execution_plan:
            user_parts.append(f"\nExecution plan to follow:\n{execution_plan}")

        messages: list[dict] = [{"role": "system", "content": system_prompt}]

        if cleaned_memory:
            messages.append(
                {
                    "role": "system",
                    "content": (
                        "You have remembered user information. "
                        "Use it naturally in conversation. "
                        "Do not say you cannot remember the user."
                    ),
                }
            )
        
        # Append chat history
        messages.extend(chat_history)
        
        messages.append({"role": "user", "content": "\n".join(user_parts)})

        tools_spec = self._tools.list_tools()

        for step in range(1, self._max_iterations + 1):
            # THINK
            yield self._emit("thought", f"Step {step}: Analyzing...", {"step": step})

            messages, compressed = self._maybe_compress_context(messages, force_compress)

            if compressed:
                yield self._emit(
                    "context_compressed",
                    "已压缩旧消息以节省上下文。",
                    {"strategy": "summary", "keep_last": self._compress_keep_last},
                )
                force_compress = False

            token_snapshot = self._build_token_snapshot(messages)
            yield self._emit("token_status", "", token_snapshot)

            response = self._llm.chat_with_tools(messages, tools_spec)

            msg = response["message"]
            usage = response.get("usage") or {}
            if usage:
                yield self._emit("token_status", "", self._build_token_snapshot(messages, usage))

            if msg.content:
                yield self._emit("thought", msg.content, {"step": step})

            # No tool call -> short = casual chat, long = prompt model to use tools
            if not msg.tool_calls:
                content = (msg.content or "").strip()
                if content and len(content) < 200:
                    yield self._emit("answer", content, {"step": step, "mode": "natural_exit"})
                    yield self._emit("done", "", {})
                    return
                # Add assistant message so model sees it already said this
                messages.append({"role": "assistant", "content": content})
                messages.append({"role": "system", "content": "You must call a tool (ask_user or finish) to proceed. Do not repeat the same text."})
                continue

            # ACT — validate arguments and collect tool calls to process
            msg_dict = msg.model_dump()
            missing_finish_answer = False
            bad_ask_user = False
            tool_call_items = []  # (tc_dict, tool_name, arguments)

            for tc_dict in msg_dict.get("tool_calls", []):
                func = tc_dict["function"]
                tool_name = func["name"]
                raw_args = func["arguments"]

                try:
                    arguments = json.loads(raw_args)
                except (json.JSONDecodeError, TypeError):
                    arguments = {}
                    func["arguments"] = "{}"

                tool_call_items.append((tc_dict, tool_name, arguments))

            # Build tool_results and assistant tool_calls together
            # so DeepSeek never sees assistant(tool_calls) without tool responses
            assistant_msg = msg_dict
            tool_results = []

            for tc_dict, tool_name, arguments in tool_call_items:
                if tool_name == "finish" and (not arguments or not str(arguments.get("answer", "")).strip()):
                    missing_finish_answer = True
                    tool_results.append({
                        "role": "tool",
                        "tool_call_id": tc_dict["id"],
                        "content": json.dumps({"status": "error", "message": "Missing answer field"}, ensure_ascii=False),
                    })
                    continue

                if tool_name == "ask_user" and (not arguments or not arguments.get("questions")):
                    bad_ask_user = True
                    tool_results.append({
                        "role": "tool",
                        "tool_call_id": tc_dict["id"],
                        "content": json.dumps({"status": "error", "message": "Missing questions field"}, ensure_ascii=False),
                    })
                    continue

                yield self._emit(
                    "action",
                    f"Calling tool: {tool_name}({json.dumps(arguments, ensure_ascii=False)})",
                    {"step": step, "tool": tool_name},
                )

                result = self._execute_with_retry(tool_name, arguments)
                result_str = (
                    json.dumps(result, ensure_ascii=False, default=str)
                    if not isinstance(result, str)
                    else result
                )
                yield self._emit(
                    "observation",
                    result_str[:2000],
                    {"step": step, "tool": tool_name},
                )

                tool_results.append({
                    "role": "tool",
                    "tool_call_id": tc_dict["id"],
                    "content": result_str[:4000],
                })

                # Post-execution: activate_skill side effect
                if tool_name == "activate_skill":
                    try:
                        pr = json.loads(result_str)
                        if pr.get("status") == "activated" and pr.get("instructions"):
                            messages.append({
                                "role": "system",
                                "content": f"[Skill Activated: {pr.get('name')}]\nInstructions to follow:\n{pr['instructions']}"
                            })
                    except Exception:
                        pass

                # Detect finish -> extract answer and return
                if tool_name == "finish":
                    try:
                        pr = json.loads(result_str)
                        answer = pr.get("answer", "")
                        if answer and answer.strip():
                            # Flush messages before returning
                            messages.append(assistant_msg)
                            messages.extend(tool_results)
                            yield self._emit("answer", answer, {"step": step})
                            yield self._emit("done", "", {})
                            return
                    except (json.JSONDecodeError, AttributeError):
                        pass

                # Detect ask_user -> emit and return
                if tool_name == "ask_user":
                    try:
                        pr = json.loads(result_str)
                        if pr.get("status") == "waiting_for_user":
                            messages.append(assistant_msg)
                            messages.extend(tool_results)
                            yield self._emit(
                                "ask_user",
                                pr.get("message", ""),
                                {"questions": pr.get("questions", [])},
                            )
                            yield self._emit("done", "", {})
                            return
                    except (json.JSONDecodeError, AttributeError):
                        pass

            # Append assistant(tool_calls) + all tool results together
            messages.append(assistant_msg)
            messages.extend(tool_results)

            if missing_finish_answer or bad_ask_user:
                if bad_ask_user:
                    messages.append({
                        "role": "system",
                        "content": "ask_user was called without questions. You must provide at least one question with 2+ options.",
                    })
                continue

        yield self._emit(
            "error",
            "Maximum iterations reached without final answer.",
            {"iterations": self._max_iterations},
        )
        yield self._emit("done", "", {})

    def _build_token_snapshot(self, messages: list[dict], usage: dict | None = None) -> dict:
        history_messages = self._history_messages(messages)
        history_tokens = self._estimate_tokens(history_messages)
        input_tokens = self._estimate_tokens(messages)
        output_budget = max(256, min(self._llm.max_tokens, self._max_context_tokens - input_tokens))
        utilization = min(1.0, input_tokens / max(1, self._max_context_tokens))
        snapshot = {
            "input_tokens": input_tokens,
            "history_tokens": history_tokens,
            "output_budget": output_budget,
            "max_context_tokens": self._max_context_tokens,
            "utilization": utilization,
        }
        if usage:
            snapshot.update({
                "prompt_tokens": usage.get("prompt_tokens"),
                "completion_tokens": usage.get("completion_tokens"),
                "total_tokens": usage.get("total_tokens"),
            })
        return snapshot

    def _history_messages(self, messages: list[dict]) -> list[dict]:
        if len(messages) <= 2:
            return []
        return messages[1:-1]

    def _estimate_tokens(self, messages: list[dict]) -> int:
        total_chars = 0
        for msg in messages:
            content = str(msg.get("content", ""))
            total_chars += len(content)
        return max(1, total_chars // 4)

    def _maybe_compress_context(self, messages: list[dict], force: bool) -> tuple[list[dict], bool]:
        if len(messages) <= 3:
            return messages, False

        input_tokens = self._estimate_tokens(messages)
        over_threshold = input_tokens >= int(self._max_context_tokens * self._compress_threshold)
        if not force and not over_threshold:
            return messages, False

        keep_last = max(2, self._compress_keep_last)
        system_msg = messages[0]
        recent_messages = messages[-keep_last:]
        old_messages = messages[1:-keep_last]
        if not old_messages:
            return messages, False

        summary_input = "\n".join(
            f"{item.get('role', 'unknown')}: {item.get('content', '')}" for item in old_messages
        )
        prompt = (
            "你是上下文压缩器。请将下面的对话历史压缩为简洁摘要，保留用户偏好、关键决定、"
            "以及后续执行需要的上下文。输出纯文本摘要，不要添加多余解释。\n\n"
            f"对话历史:\n{summary_input[:6000]}"
        )
        summary = self._llm.chat([
            {"role": "system", "content": "你是一个严格的对话摘要器。"},
            {"role": "user", "content": prompt},
        ], temperature=0.1)
        compressed_message = {
            "role": "system",
            "content": f"Conversation summary (compressed):\n{summary.strip()}"
        }
        new_messages = [system_msg, compressed_message, *recent_messages]
        return new_messages, True

    def compress_history(self, chat_history: list[dict], keep_last: int | None = None) -> dict:
        if not chat_history:
            return {
                "summary": "",
                "compressed": False,
                "keep_last": keep_last or self._compress_keep_last,
            }

        keep_last = max(2, keep_last or self._compress_keep_last)
        old_messages = chat_history[:-keep_last]
        if not old_messages:
            return {
                "summary": "",
                "compressed": False,
                "keep_last": keep_last,
            }

        summary_input = "\n".join(
            f"{item.get('role', 'unknown')}: {item.get('content', '')}" for item in old_messages
        )
        prompt = (
            "你是上下文压缩器。请将下面的对话历史压缩为简洁摘要，保留用户偏好、关键决定、"
            "以及后续执行需要的上下文。输出纯文本摘要，不要添加多余解释。\n\n"
            f"对话历史:\n{summary_input[:6000]}"
        )
        summary = self._llm.chat([
            {"role": "system", "content": "你是一个严格的对话摘要器。"},
            {"role": "user", "content": prompt},
        ], temperature=0.1)
        return {
            "summary": summary.strip(),
            "compressed": True,
            "keep_last": keep_last,
        }

    def _execute_with_retry(self, tool_name: str, arguments: dict) -> Any:
        last_error = None
        for attempt in range(self._max_retries + 1):
            try:
                return self._tools.call(tool_name, arguments)
            except Exception as e:
                last_error = str(e)
                if attempt < self._max_retries:
                    fixed = self._self_correct(tool_name, arguments, last_error)
                    if fixed:
                        arguments = fixed
        return {"error": f"Tool {tool_name} failed after {self._max_retries + 1} attempts: {last_error}"}

    def _self_correct(self, tool_name: str, bad_args: dict, error: str) -> dict | None:
        prompt = (
            f"The tool '{tool_name}' was called with arguments "
            f"{json.dumps(bad_args, ensure_ascii=False)} but failed with error: {error}\n\n"
            "Please provide corrected arguments as JSON. Only output the JSON object, nothing else."
        )
        try:
            corrected = self._llm.chat(
                [{"role": "user", "content": prompt}], temperature=0.1
            )
            return json.loads(corrected)
        except Exception:
            return None
        
    


    @staticmethod
    def _emit(event_type: str, content: str, metadata: dict) -> str:
        return json.dumps(
            {"type": event_type, "content": content, "metadata": metadata},
            ensure_ascii=False,
        )
