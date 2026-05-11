package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.ModelRole;

public record UpdateModelProfileRequest(
    String displayName,
    String provider,
    String apiBaseUrl,
    String apiKeyRef,
    ModelRole role,
    String defaultParams,
    Boolean enabled
) {
}
