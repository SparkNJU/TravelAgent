package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.EvaluationDimension;
import java.util.List;

public record RankedModelsResponse(
    Long runId,
    String sortBy,
    EvaluationDimension dimension,
    String order,
    int total,
    List<ModelRatingResponse> ranked
) {
}
