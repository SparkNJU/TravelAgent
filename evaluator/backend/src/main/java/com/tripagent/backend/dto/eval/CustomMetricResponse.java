package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.CustomMetricType;
import java.time.LocalDateTime;

public record CustomMetricResponse(
    Long customMetricId,
    String metricName,
    CustomMetricType metricType,
    String inputFields,
    String scoringLogic,
    Double thresholdValue,
    Boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
