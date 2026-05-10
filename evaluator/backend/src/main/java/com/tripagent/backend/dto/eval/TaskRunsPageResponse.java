package com.tripagent.backend.dto.eval;

import java.util.List;

public record TaskRunsPageResponse(
    List<EvalRunResponse> items,
    int page,
    int size,
    long total,
    int totalPages,
    boolean hasNext
) {
}
