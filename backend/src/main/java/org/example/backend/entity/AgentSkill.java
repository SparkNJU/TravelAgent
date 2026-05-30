package org.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_skills")
public class AgentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "instructions", nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "scripts_code", columnDefinition = "TEXT")
    private String scriptsCode;

    @Column(name = "references_data", columnDefinition = "TEXT")
    private String referencesData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isEnabled == null) {
            isEnabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public AgentSkill() {}

    public AgentSkill(String name, String title, String description, String instructions, Boolean isEnabled, Long userId) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.isEnabled = isEnabled;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getScriptsCode() { return scriptsCode; }
    public void setScriptsCode(String scriptsCode) { this.scriptsCode = scriptsCode; }

    public String getReferencesData() { return referencesData; }
    public void setReferencesData(String referencesData) { this.referencesData = referencesData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
