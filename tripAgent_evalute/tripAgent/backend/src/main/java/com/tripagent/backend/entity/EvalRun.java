package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.RunStatus;
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
@Table(name = "eval_run")
public class EvalRun {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "run_id")
  private Long runId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false)
  private EvalTask task;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private RunStatus status;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "total_count")
  private Integer totalCount;

  @Column(name = "success_count")
  private Integer successCount;

  @Column(name = "fail_count")
  private Integer failCount;

  @PrePersist
  public void applyDefaults() {
    if (status == null) {
      status = RunStatus.READY;
    }
  }
}
