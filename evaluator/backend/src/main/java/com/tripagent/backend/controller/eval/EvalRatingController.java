package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.dto.eval.ModelRatingResponse;
import com.tripagent.backend.dto.eval.RankedModelsResponse;
import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.ModelRating;
import com.tripagent.backend.entity.enums.EvaluationDimension;
import com.tripagent.backend.repository.ModelProfileRepository;
import com.tripagent.backend.service.eval.RatingService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 评测结果接口：原始数据 + 多种排序口径，供规划平台前端按视图取数。 */
@RestController
@RequestMapping("/api/eval/runs")
public class EvalRatingController {

  private final RatingService ratingService;
  private final ModelProfileRepository modelProfileRepository;

  public EvalRatingController(RatingService ratingService, ModelProfileRepository modelProfileRepository) {
    this.ratingService = ratingService;
    this.modelProfileRepository = modelProfileRepository;
  }

  /** 全量数据：所有模型 × 所有维度，按 dimension+elo desc 排好。 */
  @GetMapping("/{runId}/ratings")
  public ResponseEntity<EvalApiResponse<List<ModelRatingResponse>>> getRatings(@PathVariable Long runId) {
    List<ModelRating> ratings = ratingService.findAll(runId);
    List<ModelRatingResponse> response = enrich(ratings);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  /**
   * 多种排序的统一入口。
   *
   * sortBy 取值：
   *   elo            按 elo（默认 desc，可指定 dimension；缺省用 OVERALL）
   *   winRate        按胜率
   *   latency        按 avgLatencyMs（asc 更优 → 默认 asc）
   *   tokens         按 avgTokens（asc 更优 → 默认 asc）
   *   completionRate 按完成率（desc 更优）
   *   safetyElo      安全维度 Elo 的快捷别名（dimension 强制 SAFETY）
   */
  @GetMapping("/{runId}/ranked")
  public ResponseEntity<EvalApiResponse<RankedModelsResponse>> getRanked(
      @PathVariable Long runId,
      @RequestParam String sortBy,
      @RequestParam(required = false) EvaluationDimension dimension,
      @RequestParam(required = false) String order,
      @RequestParam(required = false) Integer limit
  ) {
    String key = sortBy.trim().toLowerCase(Locale.ROOT);
    EvaluationDimension dim = dimension;
    String defaultOrder;

    if ("safetyelo".equals(key)) {
      key = "elo";
      dim = EvaluationDimension.SAFETY;
    }

    switch (key) {
      case "elo":
      case "winrate":
      case "completionrate":
        defaultOrder = "desc";
        break;
      case "latency":
      case "tokens":
        defaultOrder = "asc";
        break;
      default:
        throw new IllegalArgumentException("不支持的 sortBy: " + sortBy
            + " (允许：elo / winRate / latency / tokens / completionRate / safetyElo)");
    }

    boolean desc = !"asc".equalsIgnoreCase(order == null ? defaultOrder : order);

    if ("elo".equals(key) || "winrate".equals(key)) {
      if (dim == null) dim = EvaluationDimension.OVERALL;
    }

    List<ModelRating> ratings = (dim == null)
        ? ratingService.findAll(runId)
        : ratingService.findByDimension(runId, dim);

    Comparator<ModelRating> comparator = switch (key) {
      case "elo" -> Comparator.comparing(
          (ModelRating r) -> r.getElo() == null ? Double.NEGATIVE_INFINITY : r.getElo());
      case "winrate" -> Comparator.comparing(
          (ModelRating r) -> r.getWinRate() == null ? Double.NEGATIVE_INFINITY : r.getWinRate());
      case "latency" -> Comparator.comparing(
          (ModelRating r) -> r.getAvgLatencyMs() == null ? Long.MAX_VALUE : r.getAvgLatencyMs());
      case "tokens" -> Comparator.comparing(
          (ModelRating r) -> r.getAvgTokens() == null ? Long.MAX_VALUE : r.getAvgTokens());
      case "completionrate" -> Comparator.comparing(
          (ModelRating r) -> r.getCompletionRate() == null ? Double.NEGATIVE_INFINITY : r.getCompletionRate());
      default -> throw new IllegalStateException();
    };
    if (desc) {
      comparator = comparator.reversed();
    }

    List<ModelRatingResponse> all = enrich(ratings.stream().sorted(comparator).toList());

    // 对延迟/token/completion 维度，按"每模型一条"去重（取每个 modelId 的第一条）
    if ("latency".equals(key) || "tokens".equals(key) || "completionrate".equals(key)) {
      Map<Long, ModelRatingResponse> seen = new java.util.LinkedHashMap<>();
      for (ModelRatingResponse r : all) {
        seen.putIfAbsent(r.modelProfileId(), r);
      }
      all = List.copyOf(seen.values());
    }

    if (limit != null && limit > 0 && limit < all.size()) {
      all = all.subList(0, limit);
    }

    RankedModelsResponse response = new RankedModelsResponse(
        runId,
        sortBy,
        dim,
        desc ? "desc" : "asc",
        all.size(),
        all
    );
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  private List<ModelRatingResponse> enrich(List<ModelRating> ratings) {
    if (ratings.isEmpty()) return List.of();
    Map<Long, ModelProfile> profileById = new HashMap<>();
    for (ModelRating r : ratings) {
      profileById.computeIfAbsent(r.getModelProfileId(),
          id -> modelProfileRepository.findById(id).orElse(null));
    }
    return ratings.stream().map(r -> {
      ModelProfile p = profileById.get(r.getModelProfileId());
      return new ModelRatingResponse(
          r.getRatingId(),
          r.getRun().getRunId(),
          r.getModelProfileId(),
          p == null ? null : p.getModelId(),
          p == null ? null : p.getDisplayName(),
          r.getDimension(),
          r.getTheta(),
          r.getElo(),
          r.getLowerCi95(),
          r.getUpperCi95(),
          r.getNComparisons(),
          r.getNWins(),
          r.getWinRate(),
          r.getAvgLatencyMs(),
          r.getAvgTokens(),
          r.getCompletionRate()
      );
    }).toList();
  }
}
