package com.tripagent.backend.dto.eval;

import java.time.LocalDateTime;
import java.util.List;

public record ExportMonitorMetricsResponse(
    LocalDateTime fromTime,
    LocalDateTime toTime,
    long total,
    long succeeded,
    long failed,
    long pending,
    long running,
    long retried,
    long cleanedUp,
    long deleted,
    long consistencyRepaired,
    double successRate,
    double failureRate,
    double retryRate,
    List<String> alerts
) {
}
