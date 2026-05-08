package com.tripagent.backend.dto.eval;

import jakarta.validation.constraints.NotBlank;

public record CreateEvalStrategyRequest(
    @NotBlank String strategyName,
    String metricDefinitions,
    String weightConfig,
    String thresholdConfig
) {
}
