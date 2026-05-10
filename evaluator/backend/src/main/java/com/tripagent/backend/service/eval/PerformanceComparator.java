package com.tripagent.backend.service.eval;

import com.tripagent.backend.entity.enums.ComparisonResult;
import org.springframework.stereotype.Service;

/** 性能维度比较：直接根据端到端延迟决定优劣，无 LLM 调用。 */
@Service
public class PerformanceComparator {

  private static final long TIE_THRESHOLD_MS = 50L;

  public ComparisonResult compare(Long latencyA, Long latencyB) {
    if (latencyA == null && latencyB == null) {
      return ComparisonResult.TIE;
    }
    if (latencyA == null) {
      return ComparisonResult.B_PREFERRED;
    }
    if (latencyB == null) {
      return ComparisonResult.A_PREFERRED;
    }
    long diff = Math.abs(latencyA - latencyB);
    if (diff < TIE_THRESHOLD_MS) {
      return ComparisonResult.TIE;
    }
    return latencyA < latencyB ? ComparisonResult.A_PREFERRED : ComparisonResult.B_PREFERRED;
  }
}
