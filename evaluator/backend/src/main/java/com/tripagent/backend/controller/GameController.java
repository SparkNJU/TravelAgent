package com.tripagent.backend.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class GameController {

  @GetMapping("/summary")
  public Map<String, Object> summary() {
    return Map.of(
        "kpi", Map.of("activeUsers", 1280, "avgSessionMinutes", 34, "retention7d", 0.41),
        "trend", List.of(120, 132, 126, 141, 150, 163, 158)
    );
  }
}
