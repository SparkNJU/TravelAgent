package com.tripagent.backend.entity;

import com.tripagent.backend.entity.enums.ModelRole;
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
@Table(name = "model_profile")
public class ModelProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "model_profile_id")
  private Long modelProfileId;

  @Column(name = "model_id", nullable = false, unique = true, length = 120)
  private String modelId;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(name = "provider", nullable = false, length = 64)
  private String provider;

  @Column(name = "api_base_url", length = 256)
  private String apiBaseUrl;

  @Column(name = "api_key_ref", length = 64)
  private String apiKeyRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 24)
  private ModelRole role;

  @Lob
  @Column(name = "default_params")
  private String defaultParams;

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
    if (provider == null || provider.isBlank()) {
      provider = "openai_compatible";
    }
  }
}
