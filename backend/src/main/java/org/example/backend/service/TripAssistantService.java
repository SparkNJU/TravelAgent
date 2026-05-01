package org.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TripAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(TripAssistantService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.agent.base-url:http://localhost:8000/api/trip/plan}")
    private String agentBaseUrl;

    public Map<String, Object> generateTripPlan(String query, MultipartFile file, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("user_id", userId);

        if (file != null && !file.isEmpty()) {
            try {
                payload.put("file_name", file.getOriginalFilename());
                payload.put("file_mime_type", file.getContentType());
                payload.put("file_base64", Base64.getEncoder().encodeToString(file.getBytes()));
            } catch (IOException e) {
                payload.put("file_error", e.getMessage());
            }
        }

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            logger.info("Agent request payload: {}", requestBody);
            HttpRequest request = HttpRequest.newBuilder(URI.create(agentBaseUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, java.nio.charset.StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
                return data;
            }
            logger.warn("Agent error status: {}, body: {}", response.statusCode(), response.body());
            return fallbackPlan(query, file, "Agent returned HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallbackPlan(query, file, e.getMessage());
        } catch (IOException e) {
            return fallbackPlan(query, file, e.getMessage());
        }
    }

    private Map<String, Object> fallbackPlan(String query, MultipartFile file, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        String title = "旅行计划草案";
        String markdown = "# 旅行计划草案\n\n" +
                "Agent 暂不可用，以下是一个简化版草案。\n\n" +
                "## 用户需求\n" + query + "\n\n" +
                (file != null && !file.isEmpty()
                        ? "## 上传文件\n" + file.getOriginalFilename() + "\n\n"
                        : "") +
                "## 建议\n- 先确认目的地\n- 再补充天数和预算\n- 如果有攻略文件，可继续上传\n\n" +
                "## 失败原因\n" + reason;
        result.put("title", title);
        result.put("markdown", markdown);
        result.put("destination", "待确认");
        result.put("days", 3);
        result.put("images", java.util.List.of());
        result.put("sources", java.util.List.of());
        result.put("summary", "Agent 不可用时的本地降级结果");
        return result;
    }
}