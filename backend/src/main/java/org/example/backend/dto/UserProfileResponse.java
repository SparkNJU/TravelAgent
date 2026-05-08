package org.example.backend.dto;

import java.time.LocalDateTime;

public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String profilePicUrl;
    private LocalDateTime createdAt;

    public UserProfileResponse() {}

    public UserProfileResponse(Long id, String username, String email, String phone, String profilePicUrl, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.profilePicUrl = profilePicUrl;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProfilePicUrl() { return profilePicUrl; }
    public void setProfilePicUrl(String profilePicUrl) { this.profilePicUrl = profilePicUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
