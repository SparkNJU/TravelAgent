package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.ComparisonSamplingStrategy;
import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;
import java.util.List;

public record UpdateEvalTaskRequest(
    String taskName,
    String agentVersion,
    String datasetId,
    String metricSet,
    EvaluationMode evaluationMode,
    EvaluationMethod evaluationMethod,
    String evaluationDimensions,
    String strategyConfig,
    List<Long> selectedModelIds,
    Long judgeModelId,
    ComparisonSamplingStrategy comparisonSamplingStrategy,
    Boolean positionSwapEnabled
) {
}
