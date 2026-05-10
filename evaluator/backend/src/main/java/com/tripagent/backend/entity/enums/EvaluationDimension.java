package com.tripagent.backend.entity.enums;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum EvaluationDimension {
  EFFECTIVENESS,
  SAFETY,
  PERFORMANCE,
  OVERALL;

  /** 解析逗号分隔字符串为枚举集合，未识别的 token 静默忽略。 */
  public static Set<EvaluationDimension> parseDimensionSet(String raw) {
    Set<EvaluationDimension> result = new LinkedHashSet<>();
    if (raw == null || raw.isBlank()) {
      return result;
    }
    for (String token : raw.split(",")) {
      String normalized = token.trim().toUpperCase(Locale.ROOT);
      if (normalized.isEmpty()) {
        continue;
      }
      try {
        result.add(EvaluationDimension.valueOf(normalized));
      } catch (IllegalArgumentException ignored) {
      }
    }
    return result;
  }

  public static List<EvaluationDimension> asList(Set<EvaluationDimension> set) {
    return new ArrayList<>(set);
  }
}
