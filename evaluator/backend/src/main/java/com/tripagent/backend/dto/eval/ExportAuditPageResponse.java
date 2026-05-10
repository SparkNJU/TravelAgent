package com.tripagent.backend.dto.eval;

import java.util.List;

public record ExportAuditPageResponse(
    List<ExportAuditResponse> items,
    int page,
    int size,
    long total,
    int totalPages,
    boolean hasNext
) {
}
