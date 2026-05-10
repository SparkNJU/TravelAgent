package com.tripagent.backend.service.eval.ragas;

import java.util.List;
import java.util.Map;

/** Ragas service response payload from the Python agent. */
public record RagasScoreResult(
    Map<String, List<Double>> scores,
    Map<String, Double> mean,
    String warning
) {

  public Double meanFor(String metric) {
    if (mean == null) {
      return null;
    }
    return mean.get(metric);
  }

  public Double sampleScore(String metric, int sampleIndex) {
    if (scores == null) {
      return null;
    }
    List<Double> column = scores.get(metric);
    if (column == null || sampleIndex < 0 || sampleIndex >= column.size()) {
      return null;
    }
    return column.get(sampleIndex);
  }
}
