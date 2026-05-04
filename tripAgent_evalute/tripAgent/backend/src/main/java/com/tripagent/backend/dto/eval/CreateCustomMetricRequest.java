package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.CustomMetricType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCustomMetricRequest(
    @NotBlank String metricName,
    @NotNull CustomMetricType metricType,
    String inputFields,
    String scoringLogic,
    Double thresholdValue,
    Boolean enabled
) {
}
