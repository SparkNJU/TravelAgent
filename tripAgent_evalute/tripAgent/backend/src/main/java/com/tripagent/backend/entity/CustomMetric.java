package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.CustomMetricType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "custom_metric")
public class CustomMetric {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "custom_metric_id")
  private Long customMetricId;

  @Column(name = "metric_name", nullable = false, length = 120)
  private String metricName;

  @Enumerated(EnumType.STRING)
  @Column(name = "metric_type", nullable = false, length = 24)
  private CustomMetricType metricType;

  @Lob
  @Column(name = "input_fields")
  private String inputFields;

  @Lob
  @Column(name = "scoring_logic")
  private String scoringLogic;

  @Column(name = "threshold_value")
  private Double thresholdValue;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  public void applyDefaults() {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (updatedAt == null) {
      updatedAt = LocalDateTime.now();
    }
  }

  @PreUpdate
  public void touch() {
    updatedAt = LocalDateTime.now();
  }
}
