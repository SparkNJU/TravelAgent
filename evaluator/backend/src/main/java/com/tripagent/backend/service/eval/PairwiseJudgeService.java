package com.tripagent.backend.service.eval;

import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.enums.ComparisonResult;
import com.tripagent.backend.entity.enums.EvaluationDimension;
import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;
import com.tripagent.backend.service.eval.judge.JudgePromptBuilder;
import com.tripagent.backend.service.eval.judge.JudgeResponseParser;
import com.tripagent.backend.service.llm.LlmChatRequest;
import com.tripagent.backend.service.llm.LlmChatResponse;
import com.tripagent.backend.service.llm.LlmGateway;
import java.util.List;
import org.springframework.stereotype.Service;

/** 编排单次两两比较：构 prompt → 调 judge LLM → 解析 → 返回结构化结果。 */
@Service
public class PairwiseJudgeService {

  private final LlmGateway llmGateway;
  private final JudgePromptBuilder promptBuilder;
  private final JudgeResponseParser parser;

  public PairwiseJudgeService(
      LlmGateway llmGateway,
      JudgePromptBuilder promptBuilder,
      JudgeResponseParser parser
  ) {
    this.llmGateway = llmGateway;
    this.promptBuilder = promptBuilder;
    this.parser = parser;
  }

  /**
   * 执行单次 judge 调用（不含 swap，调用方负责 swap）。
   * outputA / outputB 对应判 prompt 中的 A / B 角色。
   */
  public JudgeOutcome judgeOnce(
      ModelProfile judgeModel,
      EvaluationDimension dimension,
      EvaluationMode mode,
      EvaluationMethod method,
      String input,
      String expectedOutput,
      String toolTraceA,
      String toolTraceB,
      String outputA,
      String outputB
  ) {
    List<LlmChatRequest.Message> messages = promptBuilder.build(
        dimension, mode, method, input, expectedOutput, toolTraceA, toolTraceB, outputA, outputB
    );

    LlmChatResponse response;
    try {
      response = llmGateway.invokeProfile(judgeModel, messages);
    } catch (Exception ex) {
      return new JudgeOutcome(
          ComparisonResult.INVALID,
          "judge 调用失败: " + truncate(ex.getMessage(), 200),
          0L, 0L, 0L
      );
    }

    JudgeResponseParser.ParsedJudgement parsed = parser.parse(response.text());
    return new JudgeOutcome(
        parsed.result(),
        parsed.reason(),
        response.latencyMs(),
        response.promptTokens(),
        response.completionTokens()
    );
  }

  private String truncate(String t, int max) {
    if (t == null) return "";
    return t.length() <= max ? t : t.substring(0, max) + "...";
  }

  public record JudgeOutcome(
      ComparisonResult result,
      String reason,
      long latencyMs,
      long promptTokens,
      long completionTokens
  ) {
  }
}
