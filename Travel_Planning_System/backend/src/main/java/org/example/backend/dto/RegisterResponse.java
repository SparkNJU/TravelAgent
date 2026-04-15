package org.example.backend.dto;

public class RegisterResponse {
    private boolean success;
    private String message;
    private Long userId;

    public RegisterResponse(boolean success, String message, Long userId) {
        this.success = success;
        this.message = message;
        this.userId = userId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }
}
