package com.tripagent.backend.dto.eval;

import java.time.LocalDateTime;

public record EvalStrategyVersionResponse(
    Long strategyVersionId,
    Long strategyId,
    Integer version,
    String metricDefinitions,
    String weightConfig,
    String thresholdConfig,
    LocalDateTime createdAt
) {
}
