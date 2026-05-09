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
2. ACT: Call a tool to gather information, or reason through a step
3. OBSERVE: Review the tool's result

When you have gathered enough information, produce a comprehensive travel plan in Markdown format.

Important:
- Always search for up-to-date information about the destination
- Check weather forecasts for the travel dates
- If a tool call fails, try with different parameters or use a different approach
- Structure your final answer as a detailed day-by-day itinerary with specific attractions, restaurants, and activities"""


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
    ) -> Generator[str, None, None]:
        """Execute the ReAct loop. Yields SSE event JSON strings."""
        if chat_history is None:
            chat_history = []
            
        tool_descriptions = "\n".join(
            f"- {t['function']['name']}: {t['function']['description']}"
            for t in self._tools.list_tools()
        )
        system_prompt = _SYSTEM_PROMPT_TEMPLATE.format(tools=tool_descriptions)

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

            # No tool calls -> LLM is done, produce final answer
            if not msg.tool_calls:
                final = msg.content or ""
                yield self._emit("answer", final, {"step": step})
                yield self._emit("done", "", {})
                return

            # ACT
            messages.append(msg.model_dump())

            for tool_call in msg.tool_calls:
                func = tool_call.function
                tool_name = func.name

                try:
                    arguments = json.loads(func.arguments)
                except json.JSONDecodeError:
                    arguments = {}

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

                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": result_str[:4000],
                    }
                )

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
