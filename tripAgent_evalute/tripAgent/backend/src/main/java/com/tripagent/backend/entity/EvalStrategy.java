package com.tripagent.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "eval_strategy")
public class EvalStrategy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "strategy_id")
  private Long strategyId;

  @Column(name = "strategy_name", nullable = false, length = 120)
  private String strategyName;

  @Lob
  @Column(name = "metric_definitions")
  private String metricDefinitions;

  @Lob
  @Column(name = "weight_config")
  private String weightConfig;

  @Lob
  @Column(name = "threshold_config")
  private String thresholdConfig;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  public void applyDefaults() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
