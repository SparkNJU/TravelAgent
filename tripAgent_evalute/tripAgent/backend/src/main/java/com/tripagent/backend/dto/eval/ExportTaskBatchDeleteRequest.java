package com.tripagent.backend.dto.eval;

import java.util.List;

public record ExportTaskBatchDeleteRequest(List<Long> exportIds) {
}
