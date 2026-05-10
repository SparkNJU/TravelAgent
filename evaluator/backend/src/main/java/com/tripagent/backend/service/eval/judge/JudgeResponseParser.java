package com.tripagent.backend.service.eval.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.entity.enums.ComparisonResult;
import org.springframework.stereotype.Component;

/** 解析 Judge LLM 的 JSON 响应，含宽容回退。 */
@Component
public class JudgeResponseParser {

  private final ObjectMapper objectMapper;

  public JudgeResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ParsedJudgement parse(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      return new ParsedJudgement(ComparisonResult.INVALID, "judge 返回空");
    }
    String body = stripCodeFence(rawText.trim());

    try {
      JsonNode node = objectMapper.readTree(body);
      String winner = node.path("winner").asText("").trim().toUpperCase();
      String reason = node.path("reason").asText("");
      ComparisonResult result = mapWinner(winner);
      if (result == ComparisonResult.INVALID) {
        return new ParsedJudgement(ComparisonResult.INVALID, "未识别 winner: " + truncate(winner, 64));
      }
      return new ParsedJudgement(result, truncate(reason, 480));
    } catch (Exception ex) {
      return regexFallback(body, ex.getMessage());
    }
  }

  private ParsedJudgement regexFallback(String body, String parseError) {
    String upper = body.toUpperCase();
    if (upper.contains("\"WINNER\"")) {
      if (upper.contains("\"A\"")) {
        return new ParsedJudgement(ComparisonResult.A_PREFERRED, "regex fallback");
      }
      if (upper.contains("\"B\"")) {
        return new ParsedJudgement(ComparisonResult.B_PREFERRED, "regex fallback");
      }
      if (upper.contains("\"TIE\"") || upper.contains("\"EQUAL\"")) {
        return new ParsedJudgement(ComparisonResult.TIE, "regex fallback");
      }
    }
    return new ParsedJudgement(ComparisonResult.INVALID, "解析失败: " + truncate(parseError, 200));
  }

  private ComparisonResult mapWinner(String winner) {
    return switch (winner) {
      case "A" -> ComparisonResult.A_PREFERRED;
      case "B" -> ComparisonResult.B_PREFERRED;
      case "TIE", "EQUAL", "DRAW" -> ComparisonResult.TIE;
      default -> ComparisonResult.INVALID;
    };
  }

  private String stripCodeFence(String text) {
    String t = text;
    if (t.startsWith("```")) {
      int firstNewline = t.indexOf('\n');
      if (firstNewline > 0) {
        t = t.substring(firstNewline + 1);
      } else {
        t = t.substring(3);
      }
      int closingFence = t.lastIndexOf("```");
      if (closingFence >= 0) {
        t = t.substring(0, closingFence);
      }
    }
    // 找第一个 { 到对应 } (简单贪婪)
    int braceStart = t.indexOf('{');
    int braceEnd = t.lastIndexOf('}');
    if (braceStart >= 0 && braceEnd > braceStart) {
      t = t.substring(braceStart, braceEnd + 1);
    }
    return t.trim();
  }

  private String truncate(String t, int max) {
    if (t == null) return "";
    return t.length() <= max ? t : t.substring(0, max) + "...";
  }

  public record ParsedJudgement(ComparisonResult result, String reason) {
  }
}
