// src/main/java/org/example/backend/dto/CommunityPostResponse.java
package org.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommunityPostResponse {
    private Long id;
    private String title;
    private String description;
    private List<String> images;
    private String avatar;
    private String username;
    private String bio;
    private Integer likes;
    private Integer comments;
    private Integer shares;
    private List<String> tags;
    private Long originalPostId;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}