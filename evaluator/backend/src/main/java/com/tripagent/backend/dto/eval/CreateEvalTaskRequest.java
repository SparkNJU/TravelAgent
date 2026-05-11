package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.ComparisonSamplingStrategy;
import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateEvalTaskRequest(
    @NotBlank String taskName,
    @NotBlank String agentVersion,
    @NotBlank String datasetId,
    String metricSet,
    @NotNull EvaluationMode evaluationMode,
    @NotNull EvaluationMethod evaluationMethod,
    @NotBlank String evaluationDimensions,
    String strategyConfig,
    List<Long> selectedModelIds,
    Long judgeModelId,
    ComparisonSamplingStrategy comparisonSamplingStrategy,
    Boolean positionSwapEnabled
) {
}
