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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "qa_record")
public class QaRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "qa_id")
  private Long qaId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false)
  private EvalRun run;

  @Lob
  @Column(name = "input", nullable = false)
  private String input;

  @Lob
  @Column(name = "expected_output")
  private String expectedOutput;

  @Lob
  @Column(name = "actual_output")
  private String actualOutput;

  @Lob
  @Column(name = "tool_trace")
  private String toolTrace;

  @Column(name = "first_token_latency_ms")
  private Long firstTokenLatencyMs;

  @Column(name = "end_to_end_latency_ms")
  private Long endToEndLatencyMs;

  @Lob
  @Column(name = "token_usage")
  private String tokenUsage;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Lob
  @Column(name = "error_message")
  private String errorMessage;
}
