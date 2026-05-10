package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.ModelRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateModelProfileRequest(
    @NotBlank String modelId,
    @NotBlank String displayName,
    String provider,
    String apiBaseUrl,
    String apiKeyRef,
    @NotNull ModelRole role,
    String defaultParams,
    Boolean enabled
) {
}
