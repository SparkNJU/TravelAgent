package com.tripagent.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "eval.bt")
public class EvalBtRuntimeProperties {

  /**
   * Parallelism for per-sample player model invocations.
   */
  private Integer playerParallelism = 3;

  /**
   * Parallelism for per-sample judge calls (pair x dimension x swap).
   */
  private Integer judgeParallelism = 4;

  /**
   * Skip judge LLM calls when one/both players already failed for that sample.
   */
  private Boolean skipJudgeWhenPlayerFailed = true;
}
