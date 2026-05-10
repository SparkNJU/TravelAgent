package com.tripagent.backend.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.config.LlmProperties;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI 兼容 chat completions 调用，强制使用 stream=true 模式。
 *
 * <p>原因：ModelScope 等 provider 上的 thinking 模型（Qwen3 / DeepSeek-V3.1 / GLM-4.5 等），
 * 非流式调用容易返回 {@code choices:null} 或 content 为空（因为 reasoning 占满了 token 预算）。
 * 文档示例也强制使用 stream + 同时消费 reasoning_content / content。</p>
 *
 * <p>累加规则：每条 SSE chunk 的 {@code delta.content} 拼到最终 text；
 * {@code delta.reasoning_content}（如有）拼到 reasoning（仅日志用，不进入返回值）；
 * usage 一般出现在最后一条 chunk，按出现的最大值采用。</p>
 */
@Service
public class OpenAiCompatibleClient {

  private final WebClient llmWebClient;
  private final LlmProperties props;
  private final ObjectMapper objectMapper;

  public OpenAiCompatibleClient(
      @Qualifier("llmWebClient") WebClient llmWebClient,
      LlmProperties props,
      ObjectMapper objectMapper
  ) {
    this.llmWebClient = llmWebClient;
    this.props = props;
    this.objectMapper = objectMapper;
  }

  public LlmChatResponse chat(LlmChatRequest request) {
    if (request.apiKey() == null || request.apiKey().isBlank()) {
      throw new IllegalStateException("LLM 调用缺少 apiKey");
    }
    if (request.modelId() == null || request.modelId().isBlank()) {
      throw new IllegalArgumentException("LLM 调用缺少 modelId");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", request.modelId());
    body.put("messages", request.messages().stream()
        .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
        .toList());
    body.put("stream", true);
    // OpenAI stream 默认不返回 usage，必须显式开启 include_usage 才能拿到 token 计数
    body.put("stream_options", Map.of("include_usage", true));
    if (request.temperature() != null) {
      body.put("temperature", request.temperature());
    }
    if (request.maxTokens() != null) {
      body.put("max_tokens", request.maxTokens());
    }

    long start = System.currentTimeMillis();
    StringBuilder contentBuilder = new StringBuilder();
    StringBuilder reasoningBuilder = new StringBuilder();
    long[] promptTokens = {0L};
    long[] completionTokens = {0L};
    String[] finishReason = {"unknown"};

    try {
      WebClient.RequestBodySpec spec = llmWebClient.post()
          .uri(uri -> {
            String override = request.baseUrlOverride();
            if (override != null && !override.isBlank()) {
              return URI.create(toChatCompletionsUrl(override));
            }
            return uri.path("/chat/completions").build();
          })
          .header("Authorization", "Bearer " + request.apiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM);

      Flux<String> stream = spec.bodyValue(body)
          .retrieve()
          .onStatus(s -> s.isError(), resp -> resp.bodyToMono(String.class)
              .defaultIfEmpty("")
              .flatMap(b -> Mono.error(new IllegalStateException(
                  "LLM HTTP " + resp.statusCode().value() + ": " + truncate(b, 256)))))
          .bodyToFlux(String.class)
          .timeout(Duration.ofSeconds(props.getTimeoutSeconds()));

      Iterable<String> chunks = stream.toIterable();
      for (String raw : chunks) {
        if (raw == null || raw.isBlank()) {
          continue;
        }
        String chunk = raw.trim();
        // Spring 默认 SSE codec 会剥 "data: " 前缀，但稳妥起见再处理一次
        if (chunk.startsWith("data:")) {
          chunk = chunk.substring(5).trim();
        }
        if ("[DONE]".equals(chunk)) {
          break;
        }

        try {
          JsonNode node = objectMapper.readTree(chunk);
          JsonNode choices = node.path("choices");
          if (choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            JsonNode delta = first.path("delta");
            if (!delta.isMissingNode()) {
              String content = delta.path("content").asText("");
              String reasoning = delta.path("reasoning_content").asText("");
              if (!content.isEmpty()) {
                contentBuilder.append(content);
              }
              if (!reasoning.isEmpty()) {
                reasoningBuilder.append(reasoning);
              }
            }
            JsonNode fr = first.path("finish_reason");
            if (!fr.isMissingNode() && !fr.isNull()) {
              String frStr = fr.asText("");
              if (!frStr.isEmpty()) {
                finishReason[0] = frStr;
              }
            }
          }
          JsonNode usage = node.path("usage");
          if (!usage.isMissingNode() && !usage.isNull()) {
            long pt = usage.path("prompt_tokens").asLong(0);
            long ct = usage.path("completion_tokens").asLong(0);
            if (pt > 0) promptTokens[0] = pt;
            if (ct > 0) completionTokens[0] = ct;
          }
        } catch (Exception ignored) {
          // 跳过无法解析的 chunk（流末尾可能有不完整片段）
        }
      }
    } catch (WebClientResponseException ex) {
      throw new IllegalStateException(
          "LLM HTTP 异常: " + ex.getStatusCode() + " " + truncate(ex.getResponseBodyAsString(), 256), ex);
    } catch (Exception ex) {
      throw new IllegalStateException("LLM 调用失败: " + ex.getMessage(), ex);
    }

    long latency = System.currentTimeMillis() - start;
    String text = contentBuilder.toString();
    String reasoning = reasoningBuilder.toString();

    // 边缘情况：模型把 token 全花在 reasoning 上，content 为空
    // → 此时退化为返回 reasoning 的最后 200 字 + 提示，便于评测看到一些信号
    if (text.isEmpty() && !reasoning.isEmpty()) {
      String tail = reasoning.length() > 200
          ? reasoning.substring(reasoning.length() - 200) : reasoning;
      text = "[模型仅输出推理过程，未给出正式回答；推理片段尾部] " + tail;
    }

    return new LlmChatResponse(
        text,
        promptTokens[0],
        completionTokens[0],
        latency,
        finishReason[0]
    );
  }

  private String truncate(String text, int max) {
    if (text == null) return "";
    return text.length() <= max ? text : text.substring(0, max) + "...";
  }

  private String toChatCompletionsUrl(String baseUrl) {
    String trimmed = baseUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    if (trimmed.endsWith("/chat/completions")) {
      return trimmed;
    }
    return trimmed + "/chat/completions";
  }
}
