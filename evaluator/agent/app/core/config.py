import os

from pydantic import BaseModel


class Settings(BaseModel):
    app_env: str = "development"
    modelscope_api_key: str = ""
    modelscope_base_url: str = "https://api-inference.modelscope.cn/v1"
    ragas_judge_model: str = "Qwen/Qwen2.5-72B-Instruct"


def _load() -> Settings:
    return Settings(
        app_env=os.environ.get("APP_ENV", "development"),
        modelscope_api_key=os.environ.get("MODELSCOPE_API_KEY")
        or os.environ.get("MODELSCOPE_ACCESS_TOKEN", ""),
        modelscope_base_url=os.environ.get(
            "MODELSCOPE_BASE_URL",
            "https://api-inference.modelscope.cn/v1",
        ),
        ragas_judge_model=os.environ.get(
            "RAGAS_JUDGE_MODEL",
            "Qwen/Qwen2.5-72B-Instruct",
        ),
    )


settings = _load()
