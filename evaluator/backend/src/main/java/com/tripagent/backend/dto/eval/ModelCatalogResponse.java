package com.tripagent.backend.dto.eval;

import java.util.List;

public record ModelCatalogResponse(
    List<Item> players,
    List<Item> judges,
    String providersNote
) {
  public record Item(
      String modelId,
      String displayName,
      List<String> tags
  ) {
  }
}
