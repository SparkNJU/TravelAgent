package com.tripagent.backend.service.llm;

import java.util.List;

public record LlmChatRequest(
    String modelId,
    List<Message> messages,
    Double temperature,
    Integer maxTokens,
    String apiKey,
    String baseUrlOverride
) {
  public record Message(String role, String content) {
    public static Message system(String content) {
      return new Message("system", content);
    }

    public static Message user(String content) {
      return new Message("user", content);
    }
  }
}
