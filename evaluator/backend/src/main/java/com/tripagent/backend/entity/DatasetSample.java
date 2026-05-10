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
@Table(name = "dataset_sample")
public class DatasetSample {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sample_id")
  private Long sampleId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dataset_id", nullable = false)
  private Dataset dataset;

  @Column(name = "sample_key", length = 64)
  private String sampleKey;

  @Lob
  @Column(name = "input", nullable = false)
  private String input;

  @Lob
  @Column(name = "expected_output")
  private String expectedOutput;

  @Lob
  @Column(name = "meta")
  private String meta;

  @Column(name = "sort_order")
  private Integer sortOrder;
}
