package org.example.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.dto.KnowledgeSyncTurnRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.agent.knowledge-url:http://localhost:8000/api/knowledge/documents}")
    private String knowledgeDocumentsUrl;

    public KnowledgeService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public KnowledgeService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Map<String, Object> syncTurn(KnowledgeSyncTurnRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", safeTitle(request));
        payload.put("content", buildKnowledgeMarkdown(request));
        payload.put("source_type", "conversation_turn");
        payload.put("source_ref", buildSourceRef(request));
        payload.put("metadata", buildMetadata(request));

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(knowledgeDocumentsUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload),
                            StandardCharsets.UTF_8
                    ))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Knowledge center returned HTTP " + response.statusCode() + ": " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Knowledge center request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Knowledge center request interrupted", e);
        }
    }

    public String buildKnowledgeMarkdown(KnowledgeSyncTurnRequest request) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(safeTitle(request)).append("\n\n");
        appendSection(markdown, "用户问题", request.getUserMessage());
        appendSection(markdown, "Agent 执行计划", request.getPlanContent());
        appendWebSearchResults(markdown, request.getWebSearchResults());
        appendSection(markdown, "最终回答", request.getAssistantAnswer());
        markdown.append("## 元信息\n");
        markdown.append("- conversation_id: ").append(nullToEmpty(request.getConversationId())).append("\n");
        markdown.append("- turn_index: ").append(request.getTurnIndex() == null ? "" : request.getTurnIndex()).append("\n");
        return markdown.toString().trim();
    }

    private void appendSection(StringBuilder markdown, String heading, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        markdown.append("## ").append(heading).append("\n");
        markdown.append(content.trim()).append("\n\n");
    }

    private void appendWebSearchResults(StringBuilder markdown, List<KnowledgeSyncTurnRequest.WebSearchGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        markdown.append("## 联网搜索结果\n");
        int groupIndex = 1;
        for (KnowledgeSyncTurnRequest.WebSearchGroup group : groups) {
            String query = group.getQuery() == null || group.getQuery().isBlank()
                    ? "搜索" + groupIndex
                    : group.getQuery().trim();
            markdown.append("### 搜索：").append(query).append("\n");
            List<Map<String, Object>> results = group.getResults();
            if (results != null) {
                int itemIndex = 1;
                for (Map<String, Object> item : results) {
                    String title = valueAsString(item, "title", "未命名结果");
                    String link = valueAsString(item, "link", "");
                    String snippet = valueAsString(item, "snippet", "");
                    markdown.append(itemIndex).append(". ");
                    if (!link.isBlank()) {
                        markdown.append("[").append(title).append("](").append(link).append(")");
                    } else {
                        markdown.append(title);
                    }
                    if (!snippet.isBlank()) {
                        markdown.append("\n   摘要：").append(snippet);
                    }
                    markdown.append("\n");
                    itemIndex++;
                }
            }
            markdown.append("\n");
            groupIndex++;
        }
    }

    private String safeTitle(KnowledgeSyncTurnRequest request) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle().trim();
        }
        if (request.getUserMessage() != null && !request.getUserMessage().isBlank()) {
            return request.getUserMessage().trim().substring(0, Math.min(40, request.getUserMessage().trim().length()));
        }
        return "TravelAgent 对话沉淀";
    }

    private String buildSourceRef(KnowledgeSyncTurnRequest request) {
        return "conversation:" + nullToEmpty(request.getConversationId()) + ":turn-" +
                (request.getTurnIndex() == null ? "" : request.getTurnIndex());
    }

    private Map<String, Object> buildMetadata(KnowledgeSyncTurnRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.put("conversation_id", request.getConversationId());
        metadata.put("turn_index", request.getTurnIndex());
        metadata.put("contains_web_search", request.getWebSearchResults() != null && !request.getWebSearchResults().isEmpty());
        return metadata;
    }

    private static String valueAsString(Map<String, Object> item, String key, String fallback) {
        Object value = item == null ? null : item.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
