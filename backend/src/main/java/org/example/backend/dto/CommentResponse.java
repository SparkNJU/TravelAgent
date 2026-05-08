// src/main/java/org/example/backend/dto/CommentResponse.java
package org.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private String content;
    private String avatar;
    private String nickname;
    private String createdAt;
}