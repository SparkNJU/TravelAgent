package com.tripagent.backend.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.config.BtProperties;
import com.tripagent.backend.entity.EvalComparison;
import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.ModelRating;
import com.tripagent.backend.entity.QaRecord;
import com.tripagent.backend.entity.enums.ComparisonResult;
import com.tripagent.backend.entity.enums.EvaluationDimension;
import com.tripagent.backend.repository.EvalComparisonRepository;
import com.tripagent.backend.repository.EvalRunRepository;
import com.tripagent.backend.repository.ModelRatingRepository;
import com.tripagent.backend.repository.QaRecordRepository;
import com.tripagent.backend.service.eval.bt.BootstrapCi;
import com.tripagent.backend.service.eval.bt.BradleyTerryFitter;
import com.tripagent.backend.service.eval.bt.EloConverter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bradley-Terry 拟合编排：读 comparisons → 拟合 → bootstrap CI → 写 model_rating。 */
@Service
public class RatingService {

  private final EvalComparisonRepository comparisonRepository;
  private final ModelRatingRepository ratingRepository;
  private final QaRecordRepository qaRecordRepository;
  private final EvalRunRepository evalRunRepository;
  private final BtProperties btProps;
  private final ObjectMapper objectMapper;

  public RatingService(
      EvalComparisonRepository comparisonRepository,
      ModelRatingRepository ratingRepository,
      QaRecordRepository qaRecordRepository,
      EvalRunRepository evalRunRepository,
      BtProperties btProps,
      ObjectMapper objectMapper
  ) {
    this.comparisonRepository = comparisonRepository;
    this.ratingRepository = ratingRepository;
    this.qaRecordRepository = qaRecordRepository;
    this.evalRunRepository = evalRunRepository;
    this.btProps = btProps;
    this.objectMapper = objectMapper;
  }

  /**
   * 对指定 run 计算所有维度的模型 BT 评分，写入 model_rating；
   * 同时合成 OVERALL 维度（按 task.strategyConfig.weightConfig 加权）；
   * 同时附带每模型的 latency / tokens / completionRate（来自 qa_record 聚合）。
   */
  @Transactional
  public void computeAndPersist(Long runId) {
    EvalRun run = evalRunRepository.findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("run 不存在: " + runId));
    EvalTask task = run.getTask();

    // 幂等：先清空该 run 之前的 rating
    ratingRepository.deleteByRunRunId(runId);

    List<EvalComparison> all = comparisonRepository.findByRunRunIdOrderByComparisonIdAsc(runId);
    if (all.isEmpty()) {
      return;
    }

    // 收集涉及的 modelId
    LinkedHashSet<Long> modelIdSet = new LinkedHashSet<>();
    for (EvalComparison c : all) {
      modelIdSet.add(c.getModelAId());
      modelIdSet.add(c.getModelBId());
    }
    List<Long> modelIds = new ArrayList<>(modelIdSet);
    if (modelIds.size() < 2) {
      return;
    }

    // 加载 qa_record 聚合附属指标（latency/tokens/completion）
    Map<Long, ModelAggregateStats> statsByModel = aggregateQaStatsForRun(runId, modelIds);

    // 按 dimension 拆分
    Map<EvaluationDimension, List<EvalComparison>> byDim = new LinkedHashMap<>();
    for (EvalComparison c : all) {
      byDim.computeIfAbsent(c.getDimension(), k -> new ArrayList<>()).add(c);
    }

    BradleyTerryFitter fitter = new BradleyTerryFitter(btProps.getFitMaxIter(), btProps.getFitLr());
    EloConverter elo = new EloConverter(btProps.getEloAnchor(), btProps.getEloScale());
    BootstrapCi bootstrap = new BootstrapCi(fitter, elo, btProps.getBootstrapRounds());

    Map<EvaluationDimension, Map<Long, Double>> eloByDim = new LinkedHashMap<>();

    for (Map.Entry<EvaluationDimension, List<EvalComparison>> entry : byDim.entrySet()) {
      EvaluationDimension dim = entry.getKey();
      List<EvalComparison> compsOfDim = entry.getValue();

      List<Long> aSide = new ArrayList<>(compsOfDim.size());
      List<Long> bSide = new ArrayList<>(compsOfDim.size());
      List<Integer> codes = new ArrayList<>(compsOfDim.size());
      for (EvalComparison c : compsOfDim) {
        aSide.add(c.getModelAId());
        bSide.add(c.getModelBId());
        codes.add(encode(c.getResult()));
      }

      BradleyTerryFitter.AggregatedPairs agg;
      Map<Long, Double> theta;
      try {
        agg = BradleyTerryFitter.aggregate(modelIds, aSide, bSide, codes);
        theta = fitter.fit(agg.modelIds(), agg.wins());
      } catch (Exception ex) {
        // 整个维度跳过
        continue;
      }

      Map<Long, double[]> ci = bootstrap.compute(modelIds, aSide, bSide, codes);

      Map<Long, Double> dimEloMap = new LinkedHashMap<>();
      for (int i = 0; i < modelIds.size(); i++) {
        Long modelId = modelIds.get(i);
        double thetaI = theta.getOrDefault(modelId, 0D);
        double eloI = elo.toElo(thetaI);
        dimEloMap.put(modelId, eloI);

        ModelRating rating = new ModelRating();
        rating.setRun(run);
        rating.setModelProfileId(modelId);
        rating.setDimension(dim);
        rating.setTheta(round4(thetaI));
        rating.setElo(round1(eloI));
        double[] ciPair = ci.get(modelId);
        if (ciPair != null && !Double.isNaN(ciPair[0])) {
          rating.setLowerCi95(round1(ciPair[0]));
          rating.setUpperCi95(round1(ciPair[1]));
        }
        int nComp = (int) Math.round(agg.totalForModel(i));
        double nWins = agg.winsForModel(i);
        rating.setNComparisons(nComp);
        rating.setNWins(round4(nWins));
        rating.setWinRate(nComp == 0 ? 0D : round4(nWins / nComp));

        ModelAggregateStats s = statsByModel.get(modelId);
        if (s != null) {
          rating.setAvgLatencyMs(s.avgLatencyMs);
          rating.setAvgTokens(s.avgTokens);
          rating.setCompletionRate(round4(s.completionRate));
        }

        ratingRepository.save(rating);
      }
      eloByDim.put(dim, dimEloMap);
    }

    // 合成 OVERALL：按 strategyConfig.weightConfig 或默认权重 0.5/0.2/0.3 加权
    Map<String, Double> weights = parseWeights(task.getStrategyConfig());
    if (!eloByDim.isEmpty()) {
      Map<Long, Double> overallElo = new LinkedHashMap<>();
      Map<Long, Double> usedWeightSum = new HashMap<>();
      for (Map.Entry<EvaluationDimension, Map<Long, Double>> de : eloByDim.entrySet()) {
        String key = de.getKey().name().toLowerCase();
        double w = weights.getOrDefault(key, defaultWeight(de.getKey()));
        if (w <= 0) continue;
        for (Map.Entry<Long, Double> me : de.getValue().entrySet()) {
          overallElo.merge(me.getKey(), me.getValue() * w, Double::sum);
          usedWeightSum.merge(me.getKey(), w, Double::sum);
        }
      }
      for (Long modelId : modelIds) {
        double sum = overallElo.getOrDefault(modelId, 0D);
        double w = usedWeightSum.getOrDefault(modelId, 0D);
        if (w <= 0) continue;
        double overall = sum / w;
        ModelRating rating = new ModelRating();
        rating.setRun(run);
        rating.setModelProfileId(modelId);
        rating.setDimension(EvaluationDimension.OVERALL);
        rating.setElo(round1(overall));
        ModelAggregateStats s = statsByModel.get(modelId);
        if (s != null) {
          rating.setAvgLatencyMs(s.avgLatencyMs);
          rating.setAvgTokens(s.avgTokens);
          rating.setCompletionRate(round4(s.completionRate));
        }
        ratingRepository.save(rating);
      }
    }
  }

  private double defaultWeight(EvaluationDimension dim) {
    return switch (dim) {
      case EFFECTIVENESS -> 0.5D;
      case SAFETY -> 0.2D;
      case PERFORMANCE -> 0.3D;
      case OVERALL -> 0D;
    };
  }

  private int encode(ComparisonResult result) {
    return switch (result) {
      case A_PREFERRED -> 1;
      case B_PREFERRED -> 2;
      case TIE -> 3;
      case INVALID -> 0;
    };
  }

  private Map<String, Double> parseWeights(String strategyConfig) {
    if (strategyConfig == null || strategyConfig.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> root = objectMapper.readValue(
          strategyConfig, new TypeReference<Map<String, Object>>() {});
      Object weightObj = root.get("weightConfig");
      if (weightObj == null) weightObj = root.get("weights");
      if (!(weightObj instanceof Map<?, ?> map)) return Map.of();
      Map<String, Double> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (e.getValue() instanceof Number n) {
          result.put(String.valueOf(e.getKey()).toLowerCase(), n.doubleValue());
        }
      }
      return result;
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private Map<Long, ModelAggregateStats> aggregateQaStatsForRun(Long runId, List<Long> modelIds) {
    List<QaRecord> records = qaRecordRepository.findByRunRunIdOrderByQaIdAsc(runId);
    Map<Long, List<QaRecord>> byModel = new HashMap<>();
    for (QaRecord r : records) {
      Long mid = r.getModelProfileId();
      if (mid == null) continue;
      byModel.computeIfAbsent(mid, k -> new ArrayList<>()).add(r);
    }
    Map<Long, ModelAggregateStats> result = new LinkedHashMap<>();
    for (Long mid : modelIds) {
      List<QaRecord> list = byModel.getOrDefault(mid, List.of());
      if (list.isEmpty()) {
        result.put(mid, new ModelAggregateStats(0L, 0L, 0D));
        continue;
      }
      long latencySum = 0;
      long tokenSum = 0;
      int success = 0;
      for (QaRecord r : list) {
        if (r.getEndToEndLatencyMs() != null) latencySum += r.getEndToEndLatencyMs();
        tokenSum += extractTotalTokens(r.getTokenUsage());
        if (r.getErrorCode() == null) success++;
      }
      long avgLat = list.isEmpty() ? 0L : latencySum / list.size();
      long avgTok = list.isEmpty() ? 0L : tokenSum / list.size();
      double completion = (double) success / list.size();
      result.put(mid, new ModelAggregateStats(avgLat, avgTok, completion));
    }
    return result;
  }

  private long extractTotalTokens(String json) {
    if (json == null || json.isBlank()) return 0L;
    try {
      Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
      Object total = map.get("totalTokens");
      if (total instanceof Number n) return n.longValue();
    } catch (Exception ignored) {
    }
    return 0L;
  }

  private double round4(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return v;
    return Math.round(v * 10000D) / 10000D;
  }

  private double round1(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return v;
    return Math.round(v * 10D) / 10D;
  }

  /** 提供给 controller 的查询：返回所有 rating，按 dimension + elo desc。 */
  @Transactional(readOnly = true)
  public List<ModelRating> findAll(Long runId) {
    return ratingRepository.findByRunRunId(runId).stream()
        .sorted(Comparator
            .comparing(ModelRating::getDimension)
            .thenComparing(r -> r.getElo() == null ? Double.NEGATIVE_INFINITY : -r.getElo()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ModelRating> findByDimension(Long runId, EvaluationDimension dimension) {
    return ratingRepository.findByRunRunIdAndDimension(runId, dimension).stream()
        .sorted(Comparator.comparing(
            r -> r.getElo() == null ? Double.NEGATIVE_INFINITY : -r.getElo()))
        .toList();
  }

  private record ModelAggregateStats(long avgLatencyMs, long avgTokens, double completionRate) {
  }
}
