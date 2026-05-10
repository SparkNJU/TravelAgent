package com.tripagent.backend.service.llm;

public record LlmChatResponse(
    String text,
    long promptTokens,
    long completionTokens,
    long latencyMs,
    String finishReason
) {
  public long totalTokens() {
    return promptTokens + completionTokens;
  }
}
