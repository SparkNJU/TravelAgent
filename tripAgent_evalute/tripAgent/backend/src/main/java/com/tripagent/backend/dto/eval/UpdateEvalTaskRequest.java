package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;

public record UpdateEvalTaskRequest(
    String taskName,
    String agentVersion,
    String datasetId,
    String metricSet,
    EvaluationMode evaluationMode,
    EvaluationMethod evaluationMethod,
    String evaluationDimensions,
    String strategyConfig,
    Long strategyVersion
) {
}
