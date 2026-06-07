"""Agent mode: executes travel planning via ReAct (Think-Act-Observe) loop."""

from __future__ import annotations

import json
from typing import Any, Generator

from .llm_service import LLMService
from .tool_registry import ToolRegistry

_SYSTEM_PROMPT_TEMPLATE = """\
You are a travel planning assistant agent. You help users create detailed travel itineraries.

You have access to the following tools:
{tools}

Follow the ReAct pattern:
1. THINK: Analyze what information you need next
2. ACT: Call a tool to gather information
3. OBSERVE: Review the tool's result

When you have gathered enough information:
- Output the complete travel plan as your message content (for the user to see during your process)
- Call the `finish` tool with the complete travel plan in the 'answer' parameter to finalize
- You may call `suggest_questions` in the same turn before or after `finish`

Important:
- Always search for up-to-date information about the destination
- Check weather forecasts for the travel dates
- If a tool call fails, try with different parameters or use a different approach
- Structure your final answer as a detailed day-by-day itinerary with specific attractions, restaurants, and activities
- Use ask_user to clarify user preferences when essential information is missing
- Do NOT end your response without calling the `finish` tool"""


class ReActAgent:
    """Executes travel planning via Think-Act-Observe loop with self-correction."""

    def __init__(
        self,
        llm: LLMService,
        tool_registry: ToolRegistry,
        max_iterations: int = 8,
        max_retries: int = 2,
    ) -> None:
        self._llm = llm
        self._tools = tool_registry
        self._max_iterations = max_iterations
        self._max_retries = max_retries

    def run(
        self,
        query: str,
        file_summary: str = "",
        execution_plan: str = "",
        chat_history: list[dict] | None = None,
        arena_mode: bool = False,
        user_id: int = 1,
    ) -> Generator[str, None, None]:
        """Execute the ReAct loop. Yields SSE event JSON strings."""
        if chat_history is None:
            chat_history = []
            
        tool_descriptions = "\n".join(
            f"- {t['function']['name']}: {t['function']['description']}"
            for t in self._tools.list_tools()
        )
        system_prompt = _SYSTEM_PROMPT_TEMPLATE.format(tools=tool_descriptions)

        # Level 1: Fetch active skills from backend and append to system prompt
        try:
            import os
            import requests
            backend_url = os.getenv("BACKEND_URL", "http://localhost:8080")
            res = requests.get(f"{backend_url}/api/skills/active", params={"userId": user_id}, timeout=3)
            if res.status_code == 200:
                skills_data = res.json().get("data", [])
                if skills_data:
                    active_skills_desc = "\n\nYou have access to specialized Skills. If the user request matches the domain of a Skill, you MUST first call the `activate_skill(skill_name)` tool to retrieve its detailed instructions and follow them carefully.\nAvailable Skills:\n"
                    for s in skills_data:
                        active_skills_desc += f"- {s['name']}: {s['description']}\n"
                    system_prompt += active_skills_desc
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
        
        # Append chat history
        messages.extend(chat_history)
        
        messages.append({"role": "user", "content": "\n".join(user_parts)})

        tools_spec = self._tools.list_tools()

        for step in range(1, self._max_iterations + 1):
            # THINK
            yield self._emit("thought", f"Step {step}: Analyzing...", {"step": step})

            response = self._llm.chat_with_tools(messages, tools_spec)
            msg = response

            if msg.content:
                yield self._emit("thought", msg.content, {"step": step})

            # No tool calls -> continue to next iteration
            if not msg.tool_calls:
                continue

            # ACT — validate arguments and collect tool calls to process
            msg_dict = msg.model_dump()
            missing_finish_answer = False
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
                    # Add a dummy tool result to satisfy API pairing requirement
                    tool_results.append({
                        "role": "tool",
                        "tool_call_id": tc_dict["id"],
                        "content": json.dumps({"status": "error", "message": "Missing answer field"}, ensure_ascii=False),
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

            if missing_finish_answer:
                continue

        yield self._emit(
            "error",
            "Maximum iterations reached without final answer.",
            {"iterations": self._max_iterations},
        )
        yield self._emit("done", "", {})

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
