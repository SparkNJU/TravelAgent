package org.example.backend.dto;

import java.util.List;
import java.util.Map;

public class KnowledgeSyncTurnRequest {
    private String title;
    private String conversationId;
    private Integer turnIndex;
    private String userMessage;
    private String assistantAnswer;
    private String planContent;
    private List<WebSearchGroup> webSearchResults;
    private Map<String, Object> metadata;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public Integer getTurnIndex() { return turnIndex; }
    public void setTurnIndex(Integer turnIndex) { this.turnIndex = turnIndex; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public String getAssistantAnswer() { return assistantAnswer; }
    public void setAssistantAnswer(String assistantAnswer) { this.assistantAnswer = assistantAnswer; }

    public String getPlanContent() { return planContent; }
    public void setPlanContent(String planContent) { this.planContent = planContent; }

    public List<WebSearchGroup> getWebSearchResults() { return webSearchResults; }
    public void setWebSearchResults(List<WebSearchGroup> webSearchResults) { this.webSearchResults = webSearchResults; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public static class WebSearchGroup {
        private String query;
        private List<Map<String, Object>> results;

        public WebSearchGroup() {}

        public WebSearchGroup(String query, List<Map<String, Object>> results) {
            this.query = query;
            this.results = results;
        }

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public List<Map<String, Object>> getResults() { return results; }
        public void setResults(List<Map<String, Object>> results) { this.results = results; }
    }
}
