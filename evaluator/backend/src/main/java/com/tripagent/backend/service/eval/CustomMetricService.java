package com.tripagent.backend.service.eval;

import com.tripagent.backend.dto.eval.CreateCustomMetricRequest;
import com.tripagent.backend.dto.eval.CustomMetricResponse;
import com.tripagent.backend.entity.CustomMetric;
import com.tripagent.backend.repository.CustomMetricRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomMetricService {

  private final CustomMetricRepository customMetricRepository;

  public CustomMetricService(CustomMetricRepository customMetricRepository) {
    this.customMetricRepository = customMetricRepository;
  }

  @Transactional
  public CustomMetricResponse createMetric(CreateCustomMetricRequest request) {
    CustomMetric metric = new CustomMetric();
    metric.setMetricName(request.metricName().trim());
    metric.setMetricType(request.metricType());
    metric.setInputFields(request.inputFields());
    metric.setScoringLogic(request.scoringLogic());
    metric.setThresholdValue(request.thresholdValue());
    metric.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());

    CustomMetric saved = customMetricRepository.save(metric);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<CustomMetricResponse> listMetrics(Boolean enabledOnly) {
    List<CustomMetric> metrics = Boolean.TRUE.equals(enabledOnly)
        ? customMetricRepository.findByEnabledTrue()
        : customMetricRepository.findAll();
    return metrics.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<CustomMetric> getEnabledMetrics() {
    return customMetricRepository.findByEnabledTrue();
  }

  private CustomMetricResponse toResponse(CustomMetric metric) {
    return new CustomMetricResponse(
        metric.getCustomMetricId(),
        metric.getMetricName(),
        metric.getMetricType(),
        metric.getInputFields(),
        metric.getScoringLogic(),
        metric.getThresholdValue(),
        metric.getEnabled(),
        metric.getCreatedAt(),
        metric.getUpdatedAt()
    );
  }
}
