package com.tripagent.backend.service.eval;

import com.tripagent.backend.dto.eval.CreateEvalStrategyRequest;
import com.tripagent.backend.dto.eval.CreateStrategyVersionRequest;
import com.tripagent.backend.dto.eval.EvalStrategyResponse;
import com.tripagent.backend.dto.eval.EvalStrategyVersionResponse;
import com.tripagent.backend.entity.EvalStrategy;
import com.tripagent.backend.entity.EvalStrategyVersion;
import com.tripagent.backend.repository.EvalStrategyRepository;
import com.tripagent.backend.repository.EvalStrategyVersionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalStrategyService {

  private final EvalStrategyRepository evalStrategyRepository;
  private final EvalStrategyVersionRepository evalStrategyVersionRepository;

  public EvalStrategyService(
      EvalStrategyRepository evalStrategyRepository,
      EvalStrategyVersionRepository evalStrategyVersionRepository
  ) {
    this.evalStrategyRepository = evalStrategyRepository;
    this.evalStrategyVersionRepository = evalStrategyVersionRepository;
  }

  @Transactional
  public EvalStrategyResponse createStrategy(CreateEvalStrategyRequest request) {
    evalStrategyRepository.findByStrategyName(request.strategyName().trim()).ifPresent(existing -> {
      throw new IllegalArgumentException("策略名称已存在: " + request.strategyName());
    });

    EvalStrategy strategy = new EvalStrategy();
    strategy.setStrategyName(request.strategyName().trim());
    strategy.setMetricDefinitions(request.metricDefinitions());
    strategy.setWeightConfig(request.weightConfig());
    strategy.setThresholdConfig(request.thresholdConfig());

    EvalStrategy saved = evalStrategyRepository.save(strategy);
    return toStrategyResponse(saved, null);
  }

  @Transactional(readOnly = true)
  public List<EvalStrategyResponse> listStrategies() {
    return evalStrategyRepository.findAll().stream().map(this::toStrategyResponseWithLatest).toList();
  }

  @Transactional(readOnly = true)
  public EvalStrategyResponse getStrategy(Long strategyId) {
    EvalStrategy strategy = getStrategyOrThrow(strategyId);
    return toStrategyResponseWithLatest(strategy);
  }

  @Transactional
  public EvalStrategyVersionResponse createStrategyVersion(Long strategyId, CreateStrategyVersionRequest request) {
    EvalStrategy strategy = getStrategyOrThrow(strategyId);

    int nextVersion = resolveVersion(strategyId, request.version());
    evalStrategyVersionRepository.findByStrategyStrategyIdAndVersion(strategyId, nextVersion).ifPresent(existing -> {
      throw new IllegalArgumentException("策略版本已存在: strategyId=" + strategyId + ", version=" + nextVersion);
    });

    EvalStrategyVersion version = new EvalStrategyVersion();
    version.setStrategy(strategy);
    version.setVersion(nextVersion);
    version.setMetricDefinitions(nonNullOrFallback(request.metricDefinitions(), strategy.getMetricDefinitions()));
    version.setWeightConfig(nonNullOrFallback(request.weightConfig(), strategy.getWeightConfig()));
    version.setThresholdConfig(nonNullOrFallback(request.thresholdConfig(), strategy.getThresholdConfig()));

    EvalStrategyVersion saved = evalStrategyVersionRepository.save(version);
    return toVersionResponse(saved);
  }

  @Transactional(readOnly = true)
  public EvalStrategyVersion getStrategyVersionById(Long strategyVersionId) {
    return evalStrategyVersionRepository.findById(strategyVersionId)
        .orElseThrow(() -> new IllegalArgumentException("策略版本不存在: strategyVersionId=" + strategyVersionId));
  }

  private int resolveVersion(Long strategyId, Integer requestedVersion) {
    if (requestedVersion != null && requestedVersion > 0) {
      return requestedVersion;
    }

    return evalStrategyVersionRepository.findByStrategyStrategyIdOrderByVersionDesc(strategyId)
        .stream()
        .findFirst()
        .map(v -> v.getVersion() + 1)
        .orElse(1);
  }

  private EvalStrategy getStrategyOrThrow(Long strategyId) {
    return evalStrategyRepository.findById(strategyId)
        .orElseThrow(() -> new IllegalArgumentException("策略不存在: strategyId=" + strategyId));
  }

  private EvalStrategyResponse toStrategyResponseWithLatest(EvalStrategy strategy) {
    Integer latest = evalStrategyVersionRepository.findByStrategyStrategyIdOrderByVersionDesc(strategy.getStrategyId())
        .stream()
        .findFirst()
        .map(EvalStrategyVersion::getVersion)
        .orElse(null);
    return toStrategyResponse(strategy, latest);
  }

  private EvalStrategyResponse toStrategyResponse(EvalStrategy strategy, Integer latestVersion) {
    return new EvalStrategyResponse(
        strategy.getStrategyId(),
        strategy.getStrategyName(),
        strategy.getMetricDefinitions(),
        strategy.getWeightConfig(),
        strategy.getThresholdConfig(),
        strategy.getCreatedAt(),
        latestVersion
    );
  }

  private EvalStrategyVersionResponse toVersionResponse(EvalStrategyVersion version) {
    return new EvalStrategyVersionResponse(
        version.getStrategyVersionId(),
        version.getStrategy().getStrategyId(),
        version.getVersion(),
        version.getMetricDefinitions(),
        version.getWeightConfig(),
        version.getThresholdConfig(),
        version.getCreatedAt()
    );
  }

  private String nonNullOrFallback(String value, String fallback) {
    return value != null ? value : fallback;
  }
}
