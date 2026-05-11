package com.tripagent.backend.service.eval.ragas;

import com.tripagent.backend.config.LlmProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP gateway to the Python agent's Ragas scoring endpoint.
 *
 * <p>The Python service returns per-metric per-sample scores plus the mean.
 * On any failure we return a deterministic fallback (mean=0.5) with a warning so
 * an evaluation run never blocks on Ragas being down.
 */
@Service
public class RagasGatewayService {

  private static final Logger log = LoggerFactory.getLogger(RagasGatewayService.class);
  private static final double FALLBACK_SCORE = 0.5D;

  private final WebClient agentWebClient;
  private final LlmProperties llmProperties;

  public RagasGatewayService(WebClient agentWebClient, LlmProperties llmProperties) {
    this.agentWebClient = agentWebClient;
    this.llmProperties = llmProperties;
  }

  /**
   * Score a batch of samples against the requested Ragas metrics.
   *
   * @param samples the question/answer/groundTruth/contexts tuples
   * @param metrics e.g. ["faithfulness", "answer_correctness"]
   */
  public RagasScoreResult score(List<RagasSample> samples, List<String> metrics) {
    if (samples == null || samples.isEmpty()) {
      return new RagasScoreResult(Map.of(), Map.of(), "no samples");
    }
    List<String> wanted = (metrics == null || metrics.isEmpty())
        ? List.of("faithfulness")
        : metrics;

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("samples", samples.stream().map(RagasGatewayService::toBody).toList());
    body.put("metrics", wanted);

    // Ragas calls fan out per (sample × metric) and each leg is an LLM round-trip; with the
    // ModelScope default judge that's ~5–15s per leg, so we let the Python side breathe.
    int timeout = Math.max(180, llmProperties.getTimeoutSeconds() * 3);

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = agentWebClient.post()
          .uri("/eval/ragas/score")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(Map.class)
          .block(Duration.ofSeconds(timeout));

      if (response == null) {
        return fallback(samples.size(), wanted, "ragas returned null");
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> rawScores = (Map<String, Object>) response.getOrDefault("scores", Map.of());
      @SuppressWarnings("unchecked")
      Map<String, Object> rawMean = (Map<String, Object>) response.getOrDefault("mean", Map.of());

      Map<String, List<Double>> scoreMap = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : rawScores.entrySet()) {
        if (entry.getValue() instanceof List<?> col) {
          List<Double> values = new ArrayList<>(col.size());
          for (Object v : col) {
            values.add(v instanceof Number n ? n.doubleValue() : FALLBACK_SCORE);
          }
          scoreMap.put(entry.getKey(), values);
        }
      }

      Map<String, Double> meanMap = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : rawMean.entrySet()) {
        if (entry.getValue() instanceof Number n) {
          meanMap.put(entry.getKey(), n.doubleValue());
        }
      }

      Object warningObj = response.get("warning");
      String warning = warningObj == null ? null : warningObj.toString();
      return new RagasScoreResult(scoreMap, meanMap, warning);
    } catch (Exception ex) {
      log.warn("ragas scoring failed, using fallback: {}", ex.toString());
      return fallback(samples.size(), wanted, "ragas call failed: " + ex.getMessage());
    }
  }

  private RagasScoreResult fallback(int n, List<String> metrics, String warning) {
    Map<String, List<Double>> scores = new LinkedHashMap<>();
    Map<String, Double> mean = new LinkedHashMap<>();
    for (String m : metrics) {
      List<Double> col = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        col.add(FALLBACK_SCORE);
      }
      scores.put(m, col);
      mean.put(m, FALLBACK_SCORE);
    }
    return new RagasScoreResult(scores, mean, warning);
  }

  private static Map<String, Object> toBody(RagasSample sample) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("question", sample.question());
    map.put("answer", sample.answer() == null ? "" : sample.answer());
    map.put("groundTruth", sample.groundTruth() == null ? "" : sample.groundTruth());
    if (sample.contexts() != null && !sample.contexts().isEmpty()) {
      map.put("contexts", sample.contexts());
    }
    return map;
  }

  /** Single sample DTO sent to the Python service. */
  public record RagasSample(
      String question,
      String answer,
      String groundTruth,
      List<String> contexts
  ) {
    public static RagasSample of(String question, String answer, String groundTruth) {
      return new RagasSample(question, answer, groundTruth, null);
    }
  }
}
