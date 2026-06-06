package org.example.backend.dto;

public class AgentMemorySyncRequest {
    private Long userId;
    private Long sourceConversationId;
    private String triggerQuery;
    private String modelVersion;
    private Integer tokenInput;
    private Integer tokenOutput;
    private String userFactsJson;
    private String memoryMarkdown;
    private String conversationSummary;
    private String publicKnowledgeJson;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public String getTriggerQuery() {
        return triggerQuery;
    }

    public void setTriggerQuery(String triggerQuery) {
        this.triggerQuery = triggerQuery;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Integer getTokenInput() {
        return tokenInput;
    }

    public void setTokenInput(Integer tokenInput) {
        this.tokenInput = tokenInput;
    }

    public Integer getTokenOutput() {
        return tokenOutput;
    }

    public void setTokenOutput(Integer tokenOutput) {
        this.tokenOutput = tokenOutput;
    }

    public String getUserFactsJson() {
        return userFactsJson;
    }

    public void setUserFactsJson(String userFactsJson) {
        this.userFactsJson = userFactsJson;
    }

    public String getMemoryMarkdown() {
        return memoryMarkdown;
    }

    public void setMemoryMarkdown(String memoryMarkdown) {
        this.memoryMarkdown = memoryMarkdown;
    }

    public String getConversationSummary() {
        return conversationSummary;
    }

    public void setConversationSummary(String conversationSummary) {
        this.conversationSummary = conversationSummary;
    }

    public String getPublicKnowledgeJson() {
        return publicKnowledgeJson;
    }

    public void setPublicKnowledgeJson(String publicKnowledgeJson) {
        this.publicKnowledgeJson = publicKnowledgeJson;
    }
}