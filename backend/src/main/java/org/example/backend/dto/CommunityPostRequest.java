// src/main/java/org/example/backend/dto/CommunityPostRequest.java
package org.example.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class CommunityPostRequest {
    private String title;
    private String description;
    private List<String> images;
    private String avatar;
    private String nickname;
    private String bio;
    private List<String> tags;
}