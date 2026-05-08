package com.tripagent.backend.dto.eval;

import java.util.List;

public record RunCompareResponse(
    Long taskId,
    Long baselineRunId,
    Long targetRunId,
    Integer totalSamples,
    Integer changedSamples,
    List<RunMetricDiffResponse> metricDiffs,
    List<RunSampleDiffResponse> sampleDiffs
) {
}
