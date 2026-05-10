package com.tripagent.backend.service.eval;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 两两比较的抽样策略。MVP 仅实现 ALL_PAIRS。 */
@Service
public class ComparisonSamplerService {

  /** 返回所有 (i, j) 索引对，i < j。 */
  public List<int[]> allPairs(int n) {
    List<int[]> pairs = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        pairs.add(new int[]{i, j});
      }
    }
    return pairs;
  }
}
