// src/main/java/org/example/backend/dto/PlanToPostRequest.java
package org.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanToPostRequest {
    private Long planId; // 旅行规划ID
    private String title; // 可选：自定义标题
}