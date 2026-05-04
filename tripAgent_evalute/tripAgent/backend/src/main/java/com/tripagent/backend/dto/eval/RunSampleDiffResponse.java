package com.tripagent.backend.dto.eval;

public record RunSampleDiffResponse(
    Integer index,
    String input,
    String baselineOutput,
    String targetOutput,
    String baselineError,
    String targetError,
    Boolean changed
) {
}
