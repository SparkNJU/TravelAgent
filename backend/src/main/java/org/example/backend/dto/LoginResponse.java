package org.example.backend.dto;

public class LoginResponse {
    private boolean success;
    private String message;
    private String token; // For future JWT use
    private Long userId;

    private String avatar;

    public LoginResponse(boolean success, String message, String token, Long userId, String avatar) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.userId = userId;
        this.avatar = avatar;
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

    public String getAvatar() {
        return avatar;
    }
}
