package org.example.backend.dto;

import java.util.List;

public class CompressConversationRequest {
    private List<ChatMessage> chatHistory;
    private Integer keepLast;

    public List<ChatMessage> getChatHistory() {
        return chatHistory;
    }

    public void setChatHistory(List<ChatMessage> chatHistory) {
        this.chatHistory = chatHistory;
    }

    public Integer getKeepLast() {
        return keepLast;
    }

    public void setKeepLast(Integer keepLast) {
        this.keepLast = keepLast;
    }
}
