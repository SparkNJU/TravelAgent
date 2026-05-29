package org.example.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_public_knowledge")
public class AgentPublicKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_key", nullable = false, unique = true, length = 128)
    private String knowledgeKey;

    @Column(name = "knowledge_title", nullable = false, length = 255)
    private String knowledgeTitle;

    @Column(name = "knowledge_content", columnDefinition = "LONGTEXT", nullable = false)
    private String knowledgeContent;

    @Column(name = "knowledge_json", columnDefinition = "JSON")
    private String knowledgeJson;

    @Column(name = "knowledge_scope", length = 50)
    private String knowledgeScope;

    @Column(name = "contributor_user_id")
    private Long contributorUserId;

    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (knowledgeScope == null) {
            knowledgeScope = "global";
        }
        if (confidenceScore == null) {
            confidenceScore = new BigDecimal("0.80");
        }
        if (usageCount == null) {
            usageCount = 1;
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

    public String getKnowledgeKey() {
        return knowledgeKey;
    }

    public void setKnowledgeKey(String knowledgeKey) {
        this.knowledgeKey = knowledgeKey;
    }

    public String getKnowledgeTitle() {
        return knowledgeTitle;
    }

    public void setKnowledgeTitle(String knowledgeTitle) {
        this.knowledgeTitle = knowledgeTitle;
    }

    public String getKnowledgeContent() {
        return knowledgeContent;
    }

    public void setKnowledgeContent(String knowledgeContent) {
        this.knowledgeContent = knowledgeContent;
    }

    public String getKnowledgeJson() {
        return knowledgeJson;
    }

    public void setKnowledgeJson(String knowledgeJson) {
        this.knowledgeJson = knowledgeJson;
    }

    public String getKnowledgeScope() {
        return knowledgeScope;
    }

    public void setKnowledgeScope(String knowledgeScope) {
        this.knowledgeScope = knowledgeScope;
    }

    public Long getContributorUserId() {
        return contributorUserId;
    }

    public void setContributorUserId(Long contributorUserId) {
        this.contributorUserId = contributorUserId;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
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