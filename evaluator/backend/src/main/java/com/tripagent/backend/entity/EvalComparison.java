package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.ComparisonResult;
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
@Table(name = "eval_comparison")
public class EvalComparison {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "comparison_id")
  private Long comparisonId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false)
  private EvalRun run;

  @Column(name = "sample_index", nullable = false)
  private Integer sampleIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "dimension", nullable = false, length = 32)
  private EvaluationDimension dimension;

  @Column(name = "model_a_id", nullable = false)
  private Long modelAId;

  @Column(name = "model_b_id", nullable = false)
  private Long modelBId;

  @Column(name = "qa_record_a_id")
  private Long qaRecordAId;

  @Column(name = "qa_record_b_id")
  private Long qaRecordBId;

  @Column(name = "position_swap", nullable = false)
  private Boolean positionSwap;

  @Enumerated(EnumType.STRING)
  @Column(name = "result", nullable = false, length = 16)
  private ComparisonResult result;

  @Column(name = "judge_model_id")
  private Long judgeModelId;

  @Column(name = "judge_reason", length = 512)
  private String judgeReason;

  @Column(name = "judge_latency_ms")
  private Long judgeLatencyMs;

  @Column(name = "judge_prompt_tokens")
  private Long judgePromptTokens;

  @Column(name = "judge_completion_tokens")
  private Long judgeCompletionTokens;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  public void applyDefaults() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (positionSwap == null) {
      positionSwap = Boolean.FALSE;
    }
  }
}
