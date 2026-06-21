package org.example.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "messages_json", columnDefinition = "LONGTEXT")
    private String messagesJson;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "workbench_plan_id")
    private Long workbenchPlanId;

    @Column(name = "workbench_status", length = 20)
    private String workbenchStatus = "none";

    @Column(name = "workbench_error", length = 500)
    private String workbenchError;

    @Column(name = "is_streaming")
    private Boolean isStreaming = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ChatConversation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessagesJson() { return messagesJson; }
    public void setMessagesJson(String messagesJson) { this.messagesJson = messagesJson; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getWorkbenchPlanId() { return workbenchPlanId; }
    public void setWorkbenchPlanId(Long workbenchPlanId) { this.workbenchPlanId = workbenchPlanId; }

    public String getWorkbenchStatus() { return workbenchStatus; }
    public void setWorkbenchStatus(String workbenchStatus) { this.workbenchStatus = workbenchStatus; }

    public String getWorkbenchError() { return workbenchError; }
    public void setWorkbenchError(String workbenchError) { this.workbenchError = workbenchError; }

    public Boolean getIsStreaming() { return isStreaming; }
    public void setIsStreaming(Boolean isStreaming) { this.isStreaming = isStreaming; }
}
