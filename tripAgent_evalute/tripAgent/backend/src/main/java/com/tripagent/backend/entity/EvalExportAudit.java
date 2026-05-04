package com.tripagent.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "eval_export_audit")
public class EvalExportAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_id")
  private Long auditId;

  @Column(name = "export_id", nullable = false)
  private Long exportId;

  @Column(name = "action", nullable = false, length = 64)
  private String action;

  @Column(name = "operator", length = 128)
  private String operator;

  @Column(name = "source_ip", length = 128)
  private String sourceIp;

  @Column(name = "detail", length = 1024)
  private String detail;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  public void applyDefaults() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
