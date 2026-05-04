package com.tripagent.backend.dto.eval;

import java.time.LocalDateTime;
import java.util.List;

public record ExportConsistencyResponse(
    LocalDateTime checkedAt,
    long dbTaskCount,
    long fileCount,
    int missingFileTaskCount,
    int orphanFileCount,
    int repairedMissingFileTaskCount,
    int removedOrphanFileCount,
    List<Long> missingFileExportIds,
    List<String> orphanFiles
) {
}
