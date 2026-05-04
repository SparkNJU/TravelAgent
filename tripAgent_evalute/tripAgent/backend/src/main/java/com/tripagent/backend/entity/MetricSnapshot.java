package com.tripagent.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "metric_snapshot")
public class MetricSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "snapshot_id")
  private Long snapshotId;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false, unique = true)
  private EvalRun run;

  @Column(name = "task_completion_rate")
  private Double taskCompletionRate;

  @Column(name = "tool_correctness_score")
  private Double toolCorrectnessScore;

  @Column(name = "tool_efficiency_score")
  private Double toolEfficiencyScore;

  @Column(name = "first_token_p95")
  private Long firstTokenP95;

  @Column(name = "end_to_end_p95")
  private Long endToEndP95;

  @Column(name = "total_tokens")
  private Long totalTokens;

  @Column(name = "effectiveness_score")
  private Double effectivenessScore;

  @Column(name = "safety_score")
  private Double safetyScore;

  @Column(name = "performance_score")
  private Double performanceScore;

  @Lob
  @Column(name = "judge_reason")
  private String judgeReason;
}
