package com.tripagent.backend.dto.eval;

public record MetricSnapshotResponse(
    Long runId,
    Double taskCompletionRate,
    Double toolCorrectnessScore,
    Double toolEfficiencyScore,
    Long firstTokenP95,
    Long endToEndP95,
    Long totalTokens,
    Double effectivenessScore,
    Double safetyScore,
    Double performanceScore,
    String judgeReason
) {
}
