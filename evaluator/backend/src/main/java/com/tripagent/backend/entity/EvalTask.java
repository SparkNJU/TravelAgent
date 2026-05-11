package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.ComparisonSamplingStrategy;
import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;
import com.tripagent.backend.entity.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "eval_task")
public class EvalTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "task_id")
  private Long taskId;

  @Column(name = "task_name", nullable = false, length = 120)
  private String taskName;

  @Column(name = "agent_version", nullable = false, length = 32)
  private String agentVersion;

  @Column(name = "dataset_id", nullable = false, length = 120)
  private String datasetId;

  @Lob
  @Column(name = "metric_set")
  private String metricSet;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private TaskStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluation_mode", nullable = false, length = 24)
  private EvaluationMode evaluationMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluation_method", nullable = false, length = 24)
  private EvaluationMethod evaluationMethod;

  @Column(name = "evaluation_dimensions", nullable = false, length = 128)
  private String evaluationDimensions;

  @Lob
  @Column(name = "strategy_config")
  private String strategyConfig;

  @Lob
  @Column(name = "selected_model_ids")
  private String selectedModelIds;

  @Column(name = "judge_model_id")
  private Long judgeModelId;

  @Enumerated(EnumType.STRING)
  @Column(name = "comparison_sampling_strategy", length = 24)
  private ComparisonSamplingStrategy comparisonSamplingStrategy;

  @Column(name = "position_swap_enabled")
  private Boolean positionSwapEnabled;

  @PrePersist
  public void applyDefaults() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (status == null) {
      status = TaskStatus.READY;
    }
  }
}
