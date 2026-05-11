package com.tripagent.backend.dto.eval;

import java.time.LocalDateTime;

public record ExportAuditResponse(
    Long auditId,
    Long exportId,
    String action,
    String operator,
    String sourceIp,
    String detail,
    LocalDateTime createdAt
) {
}
