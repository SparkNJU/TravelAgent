"""Configuration loader: merges YAML defaults with .env overrides."""

from __future__ import annotations

import os
from pathlib import Path

import yaml
from dotenv import load_dotenv
from pydantic import BaseModel

# Load .env from the agent root directory
_agent_root = Path(__file__).resolve().parent.parent
load_dotenv(_agent_root / ".env")


class LLMConfig(BaseModel):
    base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    api_key_env: str = "DASHSCOPE_API_KEY"
    chat_model: str = "qwen-plus"
    temperature: float = 0.7
    max_tokens: int = 4096


class AgentConfig(BaseModel):
    max_iterations: int = 8
    self_correction_retries: int = 2


class ToolsConfig(BaseModel):
    serper_enabled: bool = True
    serper_api_key_env: str = "SERPER_API_KEY"
    weather_enabled: bool = True
    weather_api_key_env: str = "WEATHER_API_KEY"
    weather_base_url: str = "https://api.weatherapi.com/v1"
    file_parser_enabled: bool = True


class AppConfig(BaseModel):
    llm: LLMConfig = LLMConfig()
    agent: AgentConfig = AgentConfig()
    tools: ToolsConfig = ToolsConfig()


def load_config() -> AppConfig:
    config_dir = Path(__file__).resolve().parent
    data: dict = {}

    # 1. Load default.yaml
    default_path = config_dir / "default.yaml"
    if default_path.exists():
        with open(default_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}

    # 2. Overlay with local.yaml if it exists (gitignored)
    local_path = config_dir / "local.yaml"
    if local_path.exists():
        with open(local_path, "r", encoding="utf-8") as f:
            local_data = yaml.safe_load(f) or {}
            data = _deep_merge(data, local_data)

    # 3. Overlay with environment variables
    llm_data = data.get("llm", {})
    if os.getenv("LLM_BASE_URL"):
        llm_data["base_url"] = os.getenv("LLM_BASE_URL")
    if os.getenv("LLM_MODEL"):
        llm_data["chat_model"] = os.getenv("LLM_MODEL")
    if os.getenv("LLM_TEMPERATURE"):
        llm_data["temperature"] = float(os.getenv("LLM_TEMPERATURE"))
    if os.getenv("LLM_MAX_TOKENS"):
        llm_data["max_tokens"] = int(os.getenv("LLM_MAX_TOKENS"))

    agent_data = data.get("agent", {})
    if os.getenv("AGENT_MAX_ITERATIONS"):
        agent_data["max_iterations"] = int(os.getenv("AGENT_MAX_ITERATIONS"))
    if os.getenv("AGENT_SELF_CORRECTION_RETRIES"):
        agent_data["self_correction_retries"] = int(os.getenv("AGENT_SELF_CORRECTION_RETRIES"))

    tools_data = data.get("tools", {})
    for key in ("serper_enabled", "weather_enabled", "file_parser_enabled"):
        env_key = f"TOOLS_{key.upper()}"
        if os.getenv(env_key):
            tools_data[key] = os.getenv(env_key).lower() in ("true", "1", "yes")

    return AppConfig(**data)


def _deep_merge(base: dict, override: dict) -> dict:
    result = base.copy()
    for key, value in override.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = _deep_merge(result[key], value)
        else:
            result[key] = value
    return result


# Module-level singleton
config = load_config()
