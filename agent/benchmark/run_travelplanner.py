"""
TravelPlanner Benchmark Runner for TravelMind Agent.

Runs the TravelPlanner benchmark in sole-planning mode:
  1. Load query data + reference information
  2. Construct prompts with reference info injected
  3. Call the agent API to get travel plans
  4. Parse Markdown plans into structured JSON using LLM
  5. Evaluate against TravelPlanner constraints
  6. Save results and print metrics

Usage:
    python -m benchmark.run_travelplanner --set_type train --max_cases 5
    python -m benchmark.run_travelplanner --set_type train
    python -m benchmark.run_travelplanner --set_type validation
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

from tqdm import tqdm

# Add project root to path
_BENCHMARK_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(_BENCHMARK_DIR))

from agent_client import call_agent
from plan_parser import parse_plan_with_llm
from evaluator import (
    evaluate_all,
    load_queries,
    load_ref_info,
)


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------

def format_reference_info(ref_info: dict) -> str:
    """Convert reference_information dict into a readable string for the prompt."""
    parts = []
    for key, value in ref_info.items():
        if isinstance(value, list):
            # Format list of items
            items_str = json.dumps(value, ensure_ascii=False, indent=2)
            parts.append(f"### {key}\n{items_str}")
        elif isinstance(value, str):
            parts.append(f"### {key}\n{value}")
    return "\n\n".join(parts)


def build_prompt(query_data: dict, ref_info: dict) -> str:
    """Build the sole-planning prompt with reference information."""
    lc = query_data.get("local_constraint", {})
    if isinstance(lc, str):
        lc = eval(lc)

    # Build constraints text
    constraints = []
    if lc.get("house rule"):
        constraints.append(f"- House rule: {lc['house rule']}")
    if lc.get("cuisine"):
        constraints.append(f"- Required cuisine: {', '.join(lc['cuisine'])}")
    if lc.get("room type"):
        constraints.append(f"- Room type: {lc['room type']}")
    if lc.get("transportation"):
        constraints.append(f"- Transportation restriction: {lc['transportation']}")
    constraints_text = "\n".join(constraints) if constraints else "None"

    ref_text = format_reference_info(ref_info)

    prompt = f"""You are a professional travel planning assistant. Create a detailed travel plan based on the following information.

## Travel Requirements
{query_data['query']}

## Constraints
{constraints_text}

## Budget
${query_data['budget']}

## Reference Information
The following data is available for your planning. You MUST use entities from this data only — do NOT invent any names.

{ref_text}

## Output Format
Please output the plan as a clear day-by-day Markdown itinerary. For each day, include:
- **Day N**: City name (or "from X to Y" for travel days)
- **Transportation**: Flight details, self-driving, or taxi
- **Breakfast**: Restaurant Name, City
- **Lunch**: Restaurant Name, City
- **Dinner**: Restaurant Name, City
- **Attractions**: Place Name, City
- **Accommodation**: Accommodation Name, City (not needed on last day)

Use "-" for items not needed (e.g., no breakfast on departure day, no accommodation on last day).
"""
    return prompt


# ---------------------------------------------------------------------------
# Main runner
# ---------------------------------------------------------------------------

async def run_single(
    idx: int,
    query_data: dict,
    ref_info: dict,
    agent_url: str,
    llm_base_url: str,
    llm_api_key: str | None,
    llm_model: str,
    semaphore: asyncio.Semaphore,
) -> dict:
    """Run a single benchmark case. Returns raw result dict."""
    prompt = build_prompt(query_data, ref_info)

    result = {
        "idx": idx,
        "query": query_data["query"],
        "level": query_data.get("level", "unknown"),
        "days": query_data.get("days", 0),
        "agent_output": None,
        "parsed_plan": None,
        "error": None,
    }

    async with semaphore:
        try:
            markdown = await call_agent(
                query=prompt,
                agent_url=agent_url,
                timeout=180.0,
                mode="agent",
                generate_plan_first=True,
            )
            result["agent_output"] = markdown

            if markdown:
                parsed = parse_plan_with_llm(
                    markdown_plan=markdown,
                    llm_base_url=llm_base_url,
                    llm_api_key=llm_api_key,
                    llm_model=llm_model,
                )
                result["parsed_plan"] = parsed
        except Exception as e:
            result["error"] = str(e)

    return result


async def run_benchmark(
    set_type: str = "train",
    agent_url: str = "http://localhost:8000",
    output_dir: str | Path = "results",
    max_cases: int | None = None,
    start_idx: int | None = None,
    end_idx: int | None = None,
    indices: list[int] | None = None,
    concurrent: int = 1,
    llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    llm_api_key: str | None = None,
    llm_model: str = "deepseek-v4-flash",
) -> dict:
    """
    Run the full benchmark pipeline.

    Returns:
        Evaluation metrics dict.
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Load data
    print(f"\n{'='*60}")
    print(f"TravelPlanner Benchmark — {set_type} split")
    print(f"{'='*60}")

    queries = load_queries(set_type)
    ref_infos = load_ref_info(set_type)

    if indices is not None:
        # Select specific indices from full dataset
        queries = [queries[i] for i in indices if i < len(queries)]
        ref_infos = [ref_infos[i] for i in indices if i < len(ref_infos)]
        _original_indices = indices
    elif start_idx is not None or end_idx is not None:
        s = start_idx or 0
        e = end_idx or len(queries)
        _original_indices = list(range(s, e))
        queries = queries[s:e]
        ref_infos = ref_infos[s:e]
    else:
        _original_indices = list(range(len(queries)))

    if max_cases:
        _original_indices = _original_indices[:max_cases]
        queries = queries[:max_cases]
        ref_infos = ref_infos[:max_cases]

    n = len(queries)
    print(f"Loaded {n} test cases")
    print(f"Agent URL: {agent_url}")
    print(f"LLM model: {llm_model}")
    print(f"Concurrency: {concurrent}")
    print()

    # Run agent on all cases
    semaphore = asyncio.Semaphore(concurrent)
    tasks = [
        run_single(
            idx=_original_indices[i],
            query_data=queries[i],
            ref_info=ref_infos[i],
            agent_url=agent_url,
            llm_base_url=llm_base_url,
            llm_api_key=llm_api_key,
            llm_model=llm_model,
            semaphore=semaphore,
        )
        for i in range(n)
    ]

    print("Running agent on all cases...")
    raw_results = []
    for coro in tqdm(asyncio.as_completed(tasks), total=n, desc="Evaluating"):
        result = await coro
        raw_results.append(result)

    # Sort by idx
    raw_results.sort(key=lambda r: r["idx"])

    # Save raw results
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    raw_path = output_dir / f"travelplanner_{set_type}_{timestamp}_raw.jsonl"
    with open(raw_path, "w", encoding="utf-8") as f:
        for r in raw_results:
            f.write(json.dumps(r, ensure_ascii=False, default=str) + "\n")
    print(f"\nRaw results saved to {raw_path}")

    # Extract plans for evaluation
    plans = [r["parsed_plan"] for r in raw_results]

    # Print parse stats
    success = sum(1 for p in plans if p is not None)
    print(f"\nPlan delivery: {success}/{n} ({success/n*100:.1f}%)")
    errors = sum(1 for r in raw_results if r["error"])
    if errors:
        print(f"Errors: {errors}")

    # Evaluate
    print("\nRunning evaluation...")
    metrics = evaluate_all(queries, plans, set_type=set_type)

    # Save metrics
    eval_path = output_dir / f"travelplanner_{set_type}_{timestamp}_eval.json"
    with open(eval_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2, default=str)
    print(f"Metrics saved to {eval_path}")

    # Print results
    print(f"\n{'='*60}")
    print(f"RESULTS — TravelPlanner {set_type}")
    print(f"{'='*60}")
    for key in [
        "Delivery Rate",
        "Commonsense Constraint Micro Pass Rate",
        "Commonsense Constraint Macro Pass Rate",
        "Hard Constraint Micro Pass Rate",
        "Hard Constraint Macro Pass Rate",
        "Final Pass Rate",
    ]:
        val = metrics[key]
        print(f"  {key}: {val*100:.2f}%")

    print(f"\nDetail:")
    d = metrics["detail"]
    print(f"  Total: {d['total']}")
    print(f"  Delivered: {d['delivered']}")
    print(f"  Commonsense pass: {d['final_commonsense_pass']}")
    print(f"  Hard pass: {d['final_hard_pass']}")
    print(f"  Final pass: {d['final_all_pass']}")
    print(f"  By level: {d['by_level']}")
    print()

    return metrics


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Run TravelPlanner benchmark")
    parser.add_argument("--set_type", type=str, default="train", choices=["train", "validation"],
                        help="Dataset split to evaluate")
    parser.add_argument("--agent_url", type=str, default="http://localhost:8000",
                        help="Agent API base URL")
    parser.add_argument("--output_dir", type=str, default=str(_BENCHMARK_DIR / "results"),
                        help="Output directory for results")
    parser.add_argument("--max_cases", type=int, default=None,
                        help="Max number of cases to run (for debugging)")
    parser.add_argument("--start_idx", type=int, default=None,
                        help="Start index (inclusive) for running a subset of cases")
    parser.add_argument("--end_idx", type=int, default=None,
                        help="End index (exclusive) for running a subset of cases")
    parser.add_argument("--indices", type=str, default=None,
                        help="Comma-separated list of specific indices to run (e.g. '4,8')")
    parser.add_argument("--concurrent", type=int, default=1,
                        help="Number of concurrent agent calls")
    parser.add_argument("--llm_base_url", type=str, default="https://token-plan-cn.xiaomimimo.com/v1",
                        help="LLM API base URL for parsing")
    parser.add_argument("--llm_model", type=str, default="deepseek-v4-flash",
                        help="LLM model name for parsing")
    parser.add_argument("--llm_api_key", type=str, default=None,
                        help="LLM API key (overrides env)")
    args = parser.parse_args()

    # Load API key: CLI arg > env
    llm_api_key = args.llm_api_key or os.getenv("LLM_API_KEY", "") or os.getenv("DEEPSEEK_API_KEY", "") or os.getenv("DASHSCOPE_API_KEY", "")

    # Parse indices
    indices = None
    if args.indices:
        indices = [int(x.strip()) for x in args.indices.split(",")]

    asyncio.run(run_benchmark(
        set_type=args.set_type,
        agent_url=args.agent_url,
        output_dir=args.output_dir,
        max_cases=args.max_cases,
        start_idx=args.start_idx,
        end_idx=args.end_idx,
        indices=indices,
        concurrent=args.concurrent,
        llm_base_url=args.llm_base_url,
        llm_api_key=llm_api_key,
        llm_model=args.llm_model,
    ))


if __name__ == "__main__":
    main()
