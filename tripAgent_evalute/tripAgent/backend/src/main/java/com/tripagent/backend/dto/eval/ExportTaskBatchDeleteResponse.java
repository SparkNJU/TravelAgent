package com.tripagent.backend.dto.eval;

import java.util.List;

public record ExportTaskBatchDeleteResponse(
    int requested,
    int deleted,
    int failed,
    List<Long> deletedIds,
    List<String> errors
) {
}
