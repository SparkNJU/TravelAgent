package com.tripagent.backend.dto.eval;

import com.tripagent.backend.entity.enums.DatasetSource;
import java.time.LocalDateTime;

public record DatasetResponse(
    Long datasetId,
    String name,
    String displayName,
    DatasetSource source,
    String owner,
    Integer sampleCount,
    String description,
    Boolean enabled,
    LocalDateTime createdAt
) {
}
