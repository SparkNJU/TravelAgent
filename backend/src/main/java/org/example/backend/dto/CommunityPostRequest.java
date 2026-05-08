// src/main/java/org/example/backend/dto/CommunityPostRequest.java
package org.example.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class CommunityPostRequest {
    private String title;
    private String description;
    private List<String> images;
    private List<String> tags;
}