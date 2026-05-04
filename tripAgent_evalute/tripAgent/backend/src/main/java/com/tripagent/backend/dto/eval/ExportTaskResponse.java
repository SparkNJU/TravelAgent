package com.tripagent.backend.dto.eval;

import java.time.LocalDateTime;

public record ExportTaskResponse(
    Long exportId,
    Long taskId,
    Long baselineRunId,
    Long targetRunId,
    String format,
    String status,
    String fileName,
    String downloadUrl,
    String message,
    String createdBy,
    String source,
    String sourceIp,
    String lastOperator,
    LocalDateTime lastOperationAt,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {
}
