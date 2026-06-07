import os
from openai import OpenAI
from dotenv import load_dotenv

# Load .env
load_dotenv()

api_key = os.getenv("DASHSCOPE_API_KEY")
base_url = os.getenv("LLM_BASE_URL")

client = OpenAI(api_key=api_key, base_url=base_url)

models_to_test = [
    "Qwen/Qwen3.5-7B-Instruct",
    "Qwen/Qwen3.5-14B-Instruct",
    "Qwen/Qwen3.5-32B-Instruct",
    "deepseek-ai/DeepSeek-V4-Flash",
    "ZhipuAI/GLM-4.7-Flash"
]

tools = [
    {
        "type": "function",
        "function": {
            "name": "web_search",
            "description": "Search the web for info",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string"}
                },
                "required": ["query"]
            }
        }
    }
]

for model in models_to_test:
    print(f"Testing model: {model} ...")
    try:
        resp = client.chat.completions.create(
            model=model,
            messages=[{"role": "user", "content": "Search for the weather in Tokyo today."}],
            tools=tools,
            tool_choice="auto"
        )
        print(f"  SUCCESS! choices: {resp.choices}")
        if resp.choices and resp.choices[0].message.tool_calls:
            print(f"  Tool calls: {resp.choices[0].message.tool_calls}")
        else:
            print("  No tool calls in response.")
    except Exception as e:
        print(f"  FAILED: {str(e)}")
    print("-" * 50)
