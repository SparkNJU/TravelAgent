package org.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_agent_memory")
public class UserAgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "memory_markdown", columnDefinition = "LONGTEXT", nullable = false)
    private String memoryMarkdown;

    @Column(name = "memory_json", columnDefinition = "JSON")
    private String memoryJson;

    @Column(name = "memory_version", length = 50)
    private String memoryVersion;

    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    @Column(name = "summary_source", length = 50)
    private String summarySource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (memoryVersion == null) {
            memoryVersion = "v1";
        }
        if (summarySource == null) {
            summarySource = "conversation";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMemoryMarkdown() {
        return memoryMarkdown;
    }

    public void setMemoryMarkdown(String memoryMarkdown) {
        this.memoryMarkdown = memoryMarkdown;
    }

    public String getMemoryJson() {
        return memoryJson;
    }

    public void setMemoryJson(String memoryJson) {
        this.memoryJson = memoryJson;
    }

    public String getMemoryVersion() {
        return memoryVersion;
    }

    public void setMemoryVersion(String memoryVersion) {
        this.memoryVersion = memoryVersion;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public String getSummarySource() {
        return summarySource;
    }

    public void setSummarySource(String summarySource) {
        this.summarySource = summarySource;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}