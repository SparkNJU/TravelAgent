package com.tripagent.backend.dto.eval;

public record RunMetricDiffResponse(
    String metric,
    Double baseline,
    Double target,
    Double delta
) {
}
