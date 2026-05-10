package com.tripagent.backend.entity.enums;

public enum CustomMetricType {
  /** Mean of Ragas faithfulness score for the run. */
  RAGAS_FAITHFULNESS,
  /** Mean of Ragas answer_correctness score for the run. */
  RAGAS_ANSWER_CORRECTNESS,
  /** Rule-based score derived from end-to-end p95 latency vs threshold. */
  RULE_LATENCY_P95,
  /** Rule-based score derived from total tokens vs threshold. */
  RULE_TOKEN_BUDGET,
  /** LLM-as-Judge driven by a stored prompt template (placeholder, not yet wired). */
  JUDGE_PROMPT_TEMPLATE,

  /** @deprecated retained so previously persisted rows still load. Use {@link #RULE_LATENCY_P95} or
   *  {@link #RULE_TOKEN_BUDGET} for new metrics. */
  @Deprecated
  DETERMINISTIC,
  /** @deprecated retained so previously persisted rows still load. Use {@link #JUDGE_PROMPT_TEMPLATE}
   *  or one of the RAGAS_* values. */
  @Deprecated
  JUDGE
}
