package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.EvaluationDimension;

public record ModelRatingResponse(
    Long ratingId,
    Long runId,
    Long modelProfileId,
    String modelId,
    String displayName,
    EvaluationDimension dimension,
    Double theta,
    Double elo,
    Double lowerCi95,
    Double upperCi95,
    Integer nComparisons,
    Double nWins,
    Double winRate,
    Long avgLatencyMs,
    Long avgTokens,
    Double completionRate
) {
}
