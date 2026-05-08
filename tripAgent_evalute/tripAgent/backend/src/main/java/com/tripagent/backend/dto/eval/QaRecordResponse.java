package com.tripagent.backend.dto.eval;

public record QaRecordResponse(
    Long qaId,
    Long runId,
    String input,
    String expectedOutput,
    String actualOutput,
    String toolTrace,
    Long firstTokenLatencyMs,
    Long endToEndLatencyMs,
    String tokenUsage,
    String errorCode,
    String errorMessage
) {
}
