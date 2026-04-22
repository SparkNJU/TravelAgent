package org.example.backend.dto;

public class LoginResponse {
    private boolean success;
    private String message;
    private String token; // For future JWT use
    private Long userId;

    public LoginResponse(boolean success, String message, String token, Long userId) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.userId = userId;
    }

    public LoginResponse(boolean success, String message, String token) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.userId = null;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }
}
