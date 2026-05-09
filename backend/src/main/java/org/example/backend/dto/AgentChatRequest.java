package org.example.backend.dto;

public class AgentChatRequest {
    private String query;
    private Long userId = 1L;
    private String mode = "agent";
    private boolean generatePlanFirst = true;
    private String fileName;
    private String fileBase64;
    private String fileMimeType;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isGeneratePlanFirst() { return generatePlanFirst; }
    public void setGeneratePlanFirst(boolean generatePlanFirst) { this.generatePlanFirst = generatePlanFirst; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileBase64() { return fileBase64; }
    public void setFileBase64(String fileBase64) { this.fileBase64 = fileBase64; }

    public String getFileMimeType() { return fileMimeType; }
    public void setFileMimeType(String fileMimeType) { this.fileMimeType = fileMimeType; }
}
