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
    private String avatar; // 可选：用户头像
    private String nickname; // 可选：用户昵称
    private String bio; // 可选：用户简介
}