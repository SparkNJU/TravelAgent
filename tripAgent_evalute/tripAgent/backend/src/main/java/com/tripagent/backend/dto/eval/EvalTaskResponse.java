package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;
import com.tripagent.backend.entity.enums.TaskStatus;
import java.time.LocalDateTime;

public record EvalTaskResponse(
    Long taskId,
    String taskName,
    String agentVersion,
    String datasetId,
    String metricSet,
    TaskStatus status,
    LocalDateTime createdAt,
    EvaluationMode evaluationMode,
    EvaluationMethod evaluationMethod,
    String evaluationDimensions,
    String strategyConfig,
    Long strategyVersion
) {
}
