package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.DatasetSource;
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
@Table(name = "dataset")
public class Dataset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "dataset_id")
  private Long datasetId;

  @Column(name = "name", nullable = false, unique = true, length = 120)
  private String name;

  @Column(name = "display_name", length = 120)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 24)
  private DatasetSource source;

  @Column(name = "owner", length = 120)
  private String owner;

  @Column(name = "sample_count")
  private Integer sampleCount;

  @Column(name = "schema_hash", length = 64)
  private String schemaHash;

  @Column(name = "description", length = 512)
  private String description;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  public void applyDefaults() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
  }
}
