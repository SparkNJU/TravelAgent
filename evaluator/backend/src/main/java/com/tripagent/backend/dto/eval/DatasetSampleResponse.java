package com.tripagent.backend.dto.eval;

public record DatasetSampleResponse(
    Long sampleId,
    Long datasetId,
    String sampleKey,
    String input,
    String expectedOutput,
    Integer sortOrder
) {
}
