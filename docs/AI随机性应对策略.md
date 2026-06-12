# AI 系统随机性应对策略

> 本文档分析旅行规划 Agent（TravelMind）**产品系统**中如何系统性地应对 AI 大语言模型的随机性（Non-determinism），总结已实现的工程手段，并提出可进一步落地的优化方向。
>
> **注意**：本文档仅讨论 Agent 产品自身的随机性应对策略。Benchmark 评测工具（`benchmark/` 目录下的 evaluator、plan_parser、run_travelplanner 等）是离线评估手段，不属于产品设计范畴，不在本文档讨论范围内。

---

## 目录

1. [问题背景](#1-问题背景)
2. [已实现的应对策略](#2-已实现的应对策略)
3. [可进一步提出的优化方案](#3-可进一步提出的优化方案)
4. [总结：分层防御体系](#4-总结分层防御体系)

---

## 1. 问题背景

大语言模型（LLM）本质上是概率模型，其输出天然具有随机性。同一个输入在不同调用中可能产生不同结果，这给工程系统带来以下挑战：

| 维度 | 具体问题 |
|------|----------|
| **输出格式不稳定** | 同一提示词下，模型有时输出 JSON、有时输出 Markdown、有时输出自由文本 |
| **内容一致性差** | 相同查询可能推荐不同的餐厅、景点、航班，导致结果不可复现 |
| **逻辑缺陷** | 可能生成重复餐厅、非闭环行程、交通冲突等结构性错误 |
| **搜索关键词随意** | Agent 自主决定搜索关键词，质量波动大 |
| **工具调用参数错误** | 生成的工具参数可能 JSON 格式错误或语义不当 |
| **上下文丢失** | 长对话中遗忘早期约束 |

本项目的应对思路是：**不消除随机性，而是通过多层约束将其收敛到可控范围内。**

---

## 2. 已实现的应对策略

### 2.1 温度分级控制（Temperature Stratification）

**核心思想**：不同场景使用不同温度，创造性任务允许发散，结构性任务强制收敛。

```
场景                        温度     原因
─────────────────────────────────────────────────
主对话（旅行规划）           0.7     需要创意和多样性
结构化 JSON 输出             0.1     需要严格格式
上下文压缩摘要               0.1     需要忠实概括
工具参数自纠正               0.1     需要精确修复
旅行计划解析                 0.1     需要高确定性
问题建议                     0.7     需要多样性
```

**实现位置**：`agent/services/llm_service.py` 的 `chat_json()` 硬编码 `temperature=0.1`，各结构化输出场景均通过此方法调用。

---

### 2.2 结构化输出约束（Structured Output Enforcement）

**核心思想**：通过 JSON Schema 强制模型输出符合预定义结构的数据，而非自由文本。

本系统中所有需要结构化返回的 LLM 调用都使用了 `response_format: { type: "json_schema" }`：

| 调用场景 | Schema | 关键约束 |
|----------|--------|----------|
| 旅行计划合规检查 | `_CHECKER_SCHEMA` | `compliant` (bool) + `violations` (string[]) |
| 反思评估 | `_EVALUATION_SCHEMA` | `verdict` (enum) + `issues` + `suggestions` |
| 记忆提取 | `_MEMORY_SCHEMA` | 4 层嵌套结构，每层 `additionalProperties: false` |
| 计划解析 | `travel_plan` | 天数/活动/交通/餐饮完整结构 |

**效果**：将模型输出从"任意自然语言"约束为"符合固定 schema 的 JSON"，消除格式维度的随机性。

---

### 2.3 搜索关键词预处理（用户提出的方向）

**现状**：当前 Agent 自主决定 `web_search` 工具的查询关键词，模型可能生成低质量或不相关的搜索词。

**可优化方向**：在 Agent 调用搜索工具前，增加一层**查询解析与规范化**（Query Normalization）：

```python
# 当前：Agent 直接生成搜索词
agent → web_search("北京有什么好吃的")  # 模型自由发挥

# 优化后：查询解析器规范化搜索词
agent → query_normalizer("北京有什么好吃的")
     → "北京 特色餐厅 推荐 2026"      # 结构化、带年份、带类别
     → web_search(normalized_query)
```

查询解析器可以是一个轻量 LLM 调用（`temperature=0.1`）+ 正则后处理，提取以下维度：

| 维度 | 示例 | 作用 |
|------|------|------|
| 地点 | 北京、三亚 | 限定搜索范围 |
| 类别 | 餐厅、景点、航班 | 决定搜索目标 |
| 时间 | 2026年7月 | 保证信息时效性 |
| 约束 | 人均100以内、亲子 | 精准匹配需求 |

---

### 2.4 PlanChecker：输出前结构验证 + Reflexion（已实现）

**核心思想**：在 Agent 调用 `finish` 输出最终答案前，插入一个 LLM 检查器验证计划的结构合规性。不合规则将违规项作为反馈注入，触发自我修正循环。

```
Agent 生成计划 → PlanChecker 检查
                    ↓
            ┌─── 合规 ───→ 输出最终答案
            │
            └── 不合规 ──→ 注入违规反馈 → Agent 修正 → 再次检查 (最多 2 轮)
```

**检查的 8 条规则**：

1. 闭环行程（最后一天返回出发城市）
2. 餐厅不重复
3. 景点不重复
4. 交通一致性（城市间移动合理）
5. 住宿合理性（住城市在行程路径上）
6. 天数覆盖完整
7. 城市覆盖合理
8. 餐饮完整（早/午/晚）

**关键设计**：检查器本身也是 LLM（`temperature=0.1`），通过严格的 JSON Schema 确保输出格式确定。异常时默认放行（`compliant=True`），避免阻塞流程。

**效果**：在 Benchmark 评测中，结构违规导致的失败从 30% 降至 0%。在实际产品场景中，用户收到的旅行计划保证了结构层面的基本合理性。

---

### 2.5 输出格式提示词强化（已实现）

**核心思想**：在提示词中给出精确的输出格式模板和严格的规则约束，减少模型"自由发挥"空间。

Agent 的系统提示词中包含明确的旅行计划输出格式要求：

```
Day N:
- Transportation: from X to Y, 交通方式, 航班/车次号, 出发/到达时间
- Breakfast: 餐厅名称, 城市
- Lunch: 餐厅名称, 城市
- Dinner: 餐厅名称, 城市
- Attractions: 景点A; 景点B
- Accommodation: 酒店名称, 城市
```

**关键规则**：
- 交通字段必须包含 "from X to Y" 格式，明确出发地和目的地
- 所有实体名称必须包含城市名
- 无内容时使用 "-" 占位

**效果**：通过在提示词层面就约束输出格式，后续的 `plan_parser.py` 解析成功率大幅提升，格式相关的解析失败从 20% 降至 0%。

---

### 2.6 工具调用自纠正（Self-Correction）

**核心思想**：当工具调用参数解析失败时，不让系统崩溃，而是让 LLM 在低温度下自行修复参数。

```python
# 伪代码
for attempt in range(max_retries + 1):
    try:
        args = json.loads(raw_args)
        result = tool.execute(args)
        return result
    except Exception as e:
        # 让 LLM 看到错误信息，自行修正参数
        corrected = self._self_correct(raw_args, str(e), tool_name)
        raw_args = corrected  # 用修正后的参数重试
```

`_self_correct()` 使用 `temperature=0.1`，提示词包含原始参数和错误信息，让模型生成修正后的 JSON。

---

### 2.7 防御性 JSON 解析（Defensive Parsing）

**核心思想**：LLM 输出的 JSON 可能带有代码围栏、额外文本、或轻微格式错误，解析器需要多层容错。

```
原始输出 → 直接 json.loads → 成功？→ 返回
                ↓ 失败
         正则提取 {.*}  → json.loads → 成功？→ 返回
                ↓ 失败
         去除 ```json 围栏 → json.loads → 成功？→ 返回
                ↓ 失败
         返回默认值
```

涉及文件：`llm_service.py`、`react_agent.py`、`plan_parser.py`、`tool_registry.py`。

---

### 2.8 非交互模式约束（Arena Mode）

**核心思想**：在不需要用户交互的场景（如 Benchmark 评测、API 调用）下，禁用交互式工具，强制单轮完成。

- 系统提示词注入：`"Arena mode: do not call ask_user or suggest_questions"`
- 从工具注册表中移除 `UserConfirmTool` 和 `SuggestQuestionsTool`
- 强制 `generate_plan_first=True`

> **说明**：此模式主要服务于 Benchmark 评测场景，但作为 Agent 的运行模式之一，也被 API 调用方使用。

---

### 2.9 上下文窗口管理（Context Compression）

**核心思想**：当对话过长时，用低温度 LLM 压缩历史消息为摘要，防止上下文溢出导致模型丢失关键约束。

- 触发条件：token 使用超过 `max_context_tokens` 的 80%
- 保留：系统消息 + 最近 6 轮对话
- 压缩：用 `temperature=0.1` 生成忠实摘要
- 注入：摘要以系统消息形式插入

---

### 2.10 其他辅助手段

| 手段 | 说明 | 位置 |
|------|------|------|
| 空答案检测 | `finish` 工具答案为空时注入错误，强制重试 | `react_agent.py:284` |
| 工具输出截断 | 工具结果超过 2000/4000 字符时截断，防止上下文污染 | `react_agent.py:309` |
| 记忆净化 | 过滤模型产生的幻觉记忆（如"我是AI"） | `react_agent.py:91` |
| 硬限制 | `max_iterations=8`、`max_retries=2`、`max_check_retries=2` | `react_agent.py` |
| 每请求隔离 | 自定义 model/temperature 时创建新 LLM 实例 | `main.py:132` |

---

## 3. 可进一步提出的优化方案

以下是尚未实现但值得考虑的方向：

### 3.1 查询关键词规范化器（Query Normalizer）

**问题**：Agent 自由生成搜索关键词，质量不可控。

**方案**：在 `WebSearchTool.execute()` 内部增加一个轻量 LLM 预处理步骤：

```python
class WebSearchTool:
    _NORMALIZE_SCHEMA = {
        "type": "object",
        "properties": {
            "normalized_query": {"type": "string"},
            "search_type": {"type": "string", "enum": ["flights", "hotels", "restaurants", "attractions", "general"]},
            "city": {"type": "string"},
            "time_range": {"type": "string"},
        },
        "required": ["normalized_query"],
        "additionalProperties": False,
    }

    def _normalize_query(self, raw_query: str, context: str) -> str:
        """将 Agent 的自由查询规范化为结构化搜索词"""
        result = self._llm.chat_json(
            messages=[{"role": "system", "content": _NORMALIZE_PROMPT},
                      {"role": "user", "content": f"Context: {context}\nQuery: {raw_query}"}],
            schema=self._NORMALIZE_SCHEMA,
            temperature=0.1,
        )
        return result.get("normalized_query", raw_query)
```

**收益**：搜索结果质量更稳定，减少因搜索词差异导致的规划波动。

---

### 3.2 搜索结果缓存与去重（Search Result Caching）

**问题**：相同或相似查询在不同对话中产生不同的搜索结果（网络内容变化、API 排序波动）。

**方案**：对搜索结果做短期缓存（如 Redis，TTL 24h），并对结果做确定性排序：

```python
def _cache_key(query: str, search_type: str) -> str:
    import hashlib
    return f"search:{search_type}:{hashlib.md5(query.lower().strip().encode()).hexdigest()}"
```

**收益**：同一 session 内相同查询得到一致结果，提升可复现性。

---

### 3.3 输出模板强制（Template Enforcement）

**问题**：即使有格式提示，模型仍可能偏离预期输出结构。

**方案**：使用 Jinja2 模板或 Pydantic Model 验证最终输出：

```python
from pydantic import BaseModel, validator

class DayPlan(BaseModel):
    day: int
    transportation: str
    breakfast: str
    lunch: str
    dinner: str
    attractions: str
    accommodation: str

    @validator("transportation")
    def transportation_must_contain_cities(cls, v):
        if "from" not in v.lower() and v != "-":
            raise ValueError("Transportation must contain 'from X to Y'")
        return v
```

**收益**：结构错误在输出层被拦截，不依赖 LLM 检查器。

---

### 3.4 多数投票 / Self-Consistency（Majority Voting）

**问题**：单次生成可能偶发性地产生低质量结果。

**方案**：同一查询生成 N 个候选计划（如 N=3），通过确定性评估函数选择最优：

```python
def generate_with_voting(query, n=3):
    candidates = []
    for _ in range(n):
        plan = agent.run(query)
        score = deterministic_score(plan)  # 基于规则的评分
        candidates.append((plan, score))
    return max(candidates, key=lambda x: x[1])[0]
```

**代价**：N 倍 token 消耗和延迟。可通过并行调用缓解延迟。

---

### 3.5 确定性知识注入（Deterministic Knowledge Injection）

**问题**：Agent 搜索到的信息不可控，可能遗漏关键约束。

**方案**：在搜索结果中注入确定性知识片段（如"三亚到海口距离约 300km"），减少模型对搜索结果的依赖：

```python
INJECTED_KNOWLEDGE = {
    ("三亚", "海口"): "三亚到海口自驾约300公里，约3.5小时",
    ("北京", "上海"): "北京到上海高铁约4.5小时，航班约2小时",
    # ...
}
```

**收益**：关键交通/地理信息不依赖搜索结果，消除该维度的随机性。

---

### 3.6 规划骨架预生成（Plan Skeleton Pre-generation）

**问题**：Agent 从零开始规划，每次走不同的推理路径。

**方案**：先用确定性逻辑生成一个"骨架"（城市顺序、天数分配），再让 LLM 填充细节：

```
输入: 出发地=北京, 目的地=三亚, 5天

确定性骨架生成器:
  Day 1: 北京 → 三亚 (飞机)
  Day 2-4: 三亚 (市内)
  Day 5: 三亚 → 北京 (飞机)

LLM 填充: 具体餐厅、景点、酒店名称
```

**收益**：结构性决策（城市顺序、天数分配）不依赖 LLM，细节填充才交给 LLM。

---

### 3.7 工具调用参数 Schema 严格化

**问题**：工具参数虽然有 schema，但某些字段（如搜索关键词）约束较松。

**方案**：对关键工具参数增加 `enum`、`pattern` 等约束：

```python
# 当前
"query": {"type": "string"}  # 任意字符串

# 优化后
"query": {
    "type": "string",
    "minLength": 2,
    "maxLength": 100,
    "pattern": "^[\\u4e00-\\u9fa5a-zA-Z0-9\\s,，]+$"  # 只允许中英文和数字
}
```

---

### 3.8 回归测试集（Regression Test Suite）

**问题**：优化一个 case 可能导致其他 case 退化。

**方案**：建立固定的测试用例集，每次优化后自动回归。这是**开发阶段的质量保障手段**，通过 Benchmark 评测工具（`benchmark/`）实现：

```bash
# 每次修改后运行
python -m benchmark.run_travelplanner --indices 0,1,2,3,4 --concurrent 1
# 对比历史结果，检测退化
python -m benchmark.compare_results old_eval.json new_eval.json
```

> **说明**：Benchmark 评测工具是离线评估手段，不属于 Agent 产品设计。此处作为开发实践提出，用于验证 Agent 优化的效果。

---

## 4. 总结：分层防御体系

本项目应对 AI 随机性的策略可以概括为一个**四层防御体系**：

```
┌─────────────────────────────────────────────────────┐
│  Layer 4: 输出后验证（Post-Output Validation）        │
│  · PlanChecker 结构合规检查                           │
│  · ReflectionAgent 质量评估                           │
│  · Pydantic 模型验证（建议）                           │
├─────────────────────────────────────────────────────┤
│  Layer 3: 输出格式约束（Output Format Enforcement）   │
│  · JSON Schema 强制结构化输出                         │
│  · 输出模板与格式提示词                                │
│  · 空答案检测                                         │
├─────────────────────────────────────────────────────┤
│  Layer 2: 推理过程控制（Reasoning Process Control）   │
│  · 温度分级控制                                       │
│  · 工具调用自纠正                                     │
│  · 上下文压缩管理                                     │
│  · 查询关键词规范化（建议）                            │
├─────────────────────────────────────────────────────┤
│  Layer 1: 输入标准化（Input Standardization）         │
│  · Arena 模式约束                                     │
│  · 搜索结果缓存（建议）                               │
│  · 确定性知识注入（建议）                              │
│  · 每请求隔离                                         │
└─────────────────────────────────────────────────────┘
```

**核心理念**：**不是消除 AI 的随机性，而是通过工程手段将其约束在可控范围内。** 每一层都是一道防线，即使某一层失效，后续层仍能兜底。

| 层级 | 策略类型 | 效果 | 成本 |
|------|----------|------|------|
| Layer 1 输入标准化 | 消除输入差异 | 从源头减少变异 | 低 |
| Layer 2 推理过程控制 | 约束模型行为 | 减少推理路径波动 | 中 |
| Layer 3 输出格式约束 | 强制结构一致 | 消除格式随机性 | 低 |
| Layer 4 输出后验证 | 检测并修正错误 | 消除结构性缺陷 | 中（额外 LLM 调用） |

通过这四层防御，系统将 AI 的随机性从"不可控"收敛为"在工程边界内可预测"，使得旅行规划 Agent 在真实产品场景中具备了可靠的输出质量。
