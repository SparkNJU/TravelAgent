package com.tripagent.backend.dto.eval;

public record UpdateEvalStrategyRequest(
    String strategyName,
    String metricDefinitions,
    String weightConfig,
    String thresholdConfig
) {
}
