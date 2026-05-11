package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.ExportTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "eval_export_task")
public class EvalExportTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "export_id")
  private Long exportId;

  @Column(name = "task_id", nullable = false)
  private Long taskId;

  @Column(name = "baseline_run_id", nullable = false)
  private Long baselineRunId;

  @Column(name = "target_run_id", nullable = false)
  private Long targetRunId;

  @Column(name = "changed_only", nullable = false)
  private Boolean changedOnly;

  @Column(name = "format", nullable = false, length = 16)
  private String format;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private ExportTaskStatus status;

  @Column(name = "file_name", length = 256)
  private String fileName;

  @Column(name = "file_path", length = 1024)
  private String filePath;

  @Column(name = "message", length = 1024)
  private String message;

  @Column(name = "created_by", length = 128)
  private String createdBy;

  @Column(name = "source", length = 64)
  private String source;

  @Column(name = "source_ip", length = 128)
  private String sourceIp;

  @Column(name = "last_operator", length = 128)
  private String lastOperator;

  @Column(name = "last_operation_at")
  private LocalDateTime lastOperationAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @PrePersist
  public void applyDefaults() {
    if (changedOnly == null) {
      changedOnly = Boolean.TRUE;
    }
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (lastOperationAt == null) {
      lastOperationAt = LocalDateTime.now();
    }
    if (status == null) {
      status = ExportTaskStatus.PENDING;
    }
  }
}
