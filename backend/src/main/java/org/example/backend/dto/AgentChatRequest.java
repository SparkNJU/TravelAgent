package org.example.backend.dto;

import java.util.List;

public class AgentChatRequest {
    private String query;
    private Long userId = 1L;
    private String mode = "agent";
    private boolean generatePlanFirst = true;
    private String model;
    private Double temperature;
    private String fileName;
    private String fileBase64;
    private String fileMimeType;
    private List<ChatMessage> chatHistory;
    
    // Fallback for form-data stringified JSON
    private String chatHistoryJson;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isGeneratePlanFirst() { return generatePlanFirst; }
    public void setGeneratePlanFirst(boolean generatePlanFirst) { this.generatePlanFirst = generatePlanFirst; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileBase64() { return fileBase64; }
    public void setFileBase64(String fileBase64) { this.fileBase64 = fileBase64; }

    public String getFileMimeType() { return fileMimeType; }
    public void setFileMimeType(String fileMimeType) { this.fileMimeType = fileMimeType; }

    public List<ChatMessage> getChatHistory() { return chatHistory; }
    public void setChatHistory(List<ChatMessage> chatHistory) { this.chatHistory = chatHistory; }

    public String getChatHistoryJson() { return chatHistoryJson; }
    public void setChatHistoryJson(String chatHistoryJson) { this.chatHistoryJson = chatHistoryJson; }
}
