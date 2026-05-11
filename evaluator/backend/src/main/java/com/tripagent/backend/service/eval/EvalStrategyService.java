package com.tripagent.backend.service.eval;

import com.tripagent.backend.dto.eval.CreateEvalStrategyRequest;
import com.tripagent.backend.dto.eval.EvalStrategyResponse;
import com.tripagent.backend.dto.eval.UpdateEvalStrategyRequest;
import com.tripagent.backend.entity.EvalStrategy;
import com.tripagent.backend.repository.EvalStrategyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalStrategyService {

  private final EvalStrategyRepository evalStrategyRepository;

  public EvalStrategyService(EvalStrategyRepository evalStrategyRepository) {
    this.evalStrategyRepository = evalStrategyRepository;
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
    return toStrategyResponse(saved);
  }

  @Transactional
  public EvalStrategyResponse updateStrategy(Long strategyId, UpdateEvalStrategyRequest request) {
    EvalStrategy strategy = getStrategyOrThrow(strategyId);

    if (request.strategyName() != null && !request.strategyName().isBlank()) {
      String nextName = request.strategyName().trim();
      evalStrategyRepository.findByStrategyName(nextName).ifPresent(existing -> {
        if (!existing.getStrategyId().equals(strategyId)) {
          throw new IllegalArgumentException("策略名称已存在: " + nextName);
        }
      });
      strategy.setStrategyName(nextName);
    }
    if (request.metricDefinitions() != null) {
      strategy.setMetricDefinitions(request.metricDefinitions());
    }
    if (request.weightConfig() != null) {
      strategy.setWeightConfig(request.weightConfig());
    }
    if (request.thresholdConfig() != null) {
      strategy.setThresholdConfig(request.thresholdConfig());
    }

    EvalStrategy saved = evalStrategyRepository.save(strategy);
    return toStrategyResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<EvalStrategyResponse> listStrategies() {
    return evalStrategyRepository.findAll().stream().map(this::toStrategyResponse).toList();
  }

  @Transactional(readOnly = true)
  public EvalStrategyResponse getStrategy(Long strategyId) {
    EvalStrategy strategy = getStrategyOrThrow(strategyId);
    return toStrategyResponse(strategy);
  }

  private EvalStrategy getStrategyOrThrow(Long strategyId) {
    return evalStrategyRepository.findById(strategyId)
        .orElseThrow(() -> new IllegalArgumentException("策略不存在: strategyId=" + strategyId));
  }

  private EvalStrategyResponse toStrategyResponse(EvalStrategy strategy) {
    return new EvalStrategyResponse(
        strategy.getStrategyId(),
        strategy.getStrategyName(),
        strategy.getMetricDefinitions(),
        strategy.getWeightConfig(),
        strategy.getThresholdConfig(),
        strategy.getCreatedAt()
    );
  }
}
