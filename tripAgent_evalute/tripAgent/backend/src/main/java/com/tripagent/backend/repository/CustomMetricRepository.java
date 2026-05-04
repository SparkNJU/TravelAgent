package com.tripagent.backend.repository;

import com.tripagent.backend.entity.CustomMetric;
import com.tripagent.backend.entity.enums.CustomMetricType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomMetricRepository extends JpaRepository<CustomMetric, Long> {

  List<CustomMetric> findByEnabledTrue();

  List<CustomMetric> findByMetricType(CustomMetricType metricType);
}
