package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.EvaluationDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "model_rating")
public class ModelRating {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "rating_id")
  private Long ratingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false)
  private EvalRun run;

  @Column(name = "model_profile_id", nullable = false)
  private Long modelProfileId;

  @Enumerated(EnumType.STRING)
  @Column(name = "dimension", nullable = false, length = 32)
  private EvaluationDimension dimension;

  @Column(name = "theta")
  private Double theta;

  @Column(name = "elo")
  private Double elo;

  @Column(name = "lower_ci_95")
  private Double lowerCi95;

  @Column(name = "upper_ci_95")
  private Double upperCi95;

  @Column(name = "n_comparisons")
  private Integer nComparisons;

  @Column(name = "n_wins")
  private Double nWins;

  @Column(name = "win_rate")
  private Double winRate;

  @Column(name = "avg_latency_ms")
  private Long avgLatencyMs;

  @Column(name = "avg_tokens")
  private Long avgTokens;

  @Column(name = "completion_rate")
  private Double completionRate;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  public void applyDefaults() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
