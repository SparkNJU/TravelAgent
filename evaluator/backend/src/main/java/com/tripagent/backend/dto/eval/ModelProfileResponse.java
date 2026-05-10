package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.ModelRole;
import java.time.LocalDateTime;

public record ModelProfileResponse(
    Long modelProfileId,
    String modelId,
    String displayName,
    String provider,
    String apiBaseUrl,
    String apiKeyRef,
    ModelRole role,
    String defaultParams,
    Boolean enabled,
    LocalDateTime createdAt
) {
}
