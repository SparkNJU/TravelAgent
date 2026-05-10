package com.tripagent.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "eval_strategy_version")
public class EvalStrategyVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "strategy_version_id")
  private Long strategyVersionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "strategy_id", nullable = false)
  private EvalStrategy strategy;

  @Column(name = "version", nullable = false)
  private Integer version;

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
