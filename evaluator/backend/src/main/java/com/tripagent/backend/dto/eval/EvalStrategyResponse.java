package com.tripagent.backend.dto.eval;

import java.time.LocalDateTime;

public record EvalStrategyResponse(
    Long strategyId,
    String strategyName,
    String metricDefinitions,
    String weightConfig,
    String thresholdConfig,
    LocalDateTime createdAt
) {
}
