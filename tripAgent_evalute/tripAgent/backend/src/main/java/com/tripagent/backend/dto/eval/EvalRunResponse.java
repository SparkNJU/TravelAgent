package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.RunStatus;
import java.time.LocalDateTime;

public record EvalRunResponse(
    Long runId,
    Long taskId,
    RunStatus status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Integer totalCount,
    Integer successCount,
    Integer failCount
) {
}
