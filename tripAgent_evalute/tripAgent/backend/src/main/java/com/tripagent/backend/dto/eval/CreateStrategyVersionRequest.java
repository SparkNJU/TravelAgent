package com.tripagent.backend.dto.eval;

public record CreateStrategyVersionRequest(
    Integer version,
    String metricDefinitions,
    String weightConfig,
    String thresholdConfig
) {
}
