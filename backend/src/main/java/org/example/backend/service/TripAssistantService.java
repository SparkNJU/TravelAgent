package org.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.dto.AgentChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Service
public class TripAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(TripAssistantService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${app.agent.base-url:http://localhost:8000/api/trip/plan}")
    private String agentBaseUrl;

    @Value("${app.agent.chat-url:http://localhost:8000/api/agent/chat}")
    private String agentChatUrl;

    @Value("${app.agent.parse-url:http://localhost:8000/api/agent/parse-plan}")
    private String agentParseUrl;

    public TripAssistantService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> parsePlanMarkdown(String markdown, String destination) {
        try {
            Map<String, Object> payload = Map.of(
                "markdown", markdown,
                "destination", destination != null ? destination : ""
            );
            String requestBody = objectMapper.writeValueAsString(payload);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(agentParseUrl, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
                if (body != null && Integer.valueOf(200).equals(body.get("code"))) {
                    return (Map<String, Object>) body.get("data");
                }
            }
            logger.warn("Failed to parse plan markdown from agent, status: {}, body: {}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            logger.error("Error calling agent parse-plan", e);
        }
        return null;
    }

    /**
     * Legacy synchronous endpoint (backward compatible).
     */
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/json;charset=UTF-8"));
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(agentBaseUrl, entity, String.class);
            int status = response.getStatusCode().value();
            if (status >= 200 && status < 300) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.getBody(), Map.class);
                return data;
            }
            logger.warn("Agent error status: {}, body: {}", status, response.getBody());
            return fallbackPlan(query, file, "Agent returned HTTP " + status);
        } catch (IOException e) {
            return fallbackPlan(query, file, e.getMessage());
        }
    }

    /**
     * SSE streaming endpoint: proxies the Python agent's SSE stream to the frontend.
     * Forwards model and temperature for per-request LLM configuration.
     */
    public SseEmitter streamAgentChat(AgentChatRequest req, MultipartFile file) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        sseExecutor.execute(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("query", req.getQuery());
                payload.put("user_id", req.getUserId());
                payload.put("mode", req.getMode());
                payload.put("generate_plan_first", req.isGeneratePlanFirst());
                if (req.isArena()) {
                    payload.put("arena", true);
                }

                if (req.getModel() != null && !req.getModel().isEmpty()) {
                    payload.put("model", req.getModel());
                }
                if (req.getTemperature() != null) {
                    payload.put("temperature", req.getTemperature());
                }

                if (req.getChatHistory() != null && !req.getChatHistory().isEmpty()) {
                    payload.put("chat_history", req.getChatHistory());
                } else if (req.getChatHistoryJson() != null && !req.getChatHistoryJson().isEmpty()) {
                    try {
                        List<Map<String, Object>> parsedHistory = objectMapper.readValue(
                            req.getChatHistoryJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                        );
                        payload.put("chat_history", parsedHistory);
                    } catch (Exception e) {
                        logger.warn("Failed to parse chatHistoryJson", e);
                    }
                }

                if (file != null && !file.isEmpty()) {
                    payload.put("file_name", file.getOriginalFilename());
                    payload.put("file_mime_type", file.getContentType());
                    payload.put("file_base64", Base64.getEncoder().encodeToString(file.getBytes()));
                }

                String jsonBody = objectMapper.writeValueAsString(payload);
                logger.info("SSE stream started: userId={}, mode={}, model={}, query={}",
                        req.getUserId(), req.getMode(), req.getModel(), req.getQuery());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(agentChatUrl))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    logger.warn("Agent SSE error: status={}, body={}", response.statusCode(), errorBody);
                    emitter.send(SseEmitter.event().name("error").data(
                            Map.of("type", "error", "content", "Agent returned HTTP " + response.statusCode())));
                    emitter.complete();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                emitter.send(SseEmitter.event().name("done").data(
                                        Map.of("type", "done", "content", "")));
                                break;
                            }
                            logger.debug("SSE event: {}", data);
                            emitter.send(SseEmitter.event().name("agent").data(data));
                        }
                    }
                }

                logger.info("SSE stream completed: userId={}", req.getUserId());
                emitter.complete();
            } catch (IOException e) {
                // Client disconnected — just log and clean up
                logger.warn("SSE client disconnected: userId={}, reason: {}", req.getUserId(), e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                logger.error("Agent SSE proxy error: userId={}", req.getUserId(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            Map.of("type", "error", "content", e.getMessage())));
                } catch (Exception ignored) {}
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> emitter.complete());

        return emitter;
    }

    public String fetchAgentAnswer(AgentChatRequest req, MultipartFile file) {
        try {
            StringBuilder answer = new StringBuilder();
            final String[] streamError = {null};
            streamAgentEvents(
                    req,
                    file,
                    event -> {
                        Object type = event.get("type");
                        if ("answer".equals(type)) {
                            Object content = event.get("content");
                            if (content != null) {
                                answer.append(content.toString());
                            }
                        } else if ("error".equals(type)) {
                            Object content = event.get("content");
                            streamError[0] = content != null ? content.toString() : "Agent error";
                        }
                    },
                    () -> {},
                    err -> streamError[0] = err
            );
            if (streamError[0] != null) {
                return streamError[0];
            }
            return answer.toString();
        } catch (Exception e) {
            logger.error("Fetch agent answer failed", e);
            return "Agent error: " + e.getMessage();
        }
    }

    public void streamAgentEvents(
            AgentChatRequest req,
            MultipartFile file,
            Consumer<Map<String, Object>> onEvent,
            Runnable onDone,
            Consumer<String> onError
    ) throws Exception {
        Map<String, Object> payload = buildAgentPayload(req, file);
        String jsonBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(agentChatUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            logger.warn("Agent SSE error: status={}, body={}", response.statusCode(), errorBody);
            onError.accept("Agent returned HTTP " + response.statusCode());
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    onDone.run();
                    return;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = objectMapper.readValue(data, Map.class);
                    onEvent.accept(event);
                } catch (Exception e) {
                    logger.debug("Failed to parse SSE data: {}", data, e);
                }
            }
        }

        onDone.run();
    }

    private Map<String, Object> buildAgentPayload(AgentChatRequest req, MultipartFile file) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", req.getQuery());
        payload.put("user_id", req.getUserId());
        payload.put("mode", req.getMode());
        payload.put("generate_plan_first", req.isGeneratePlanFirst());
        if (req.isArena()) {
            payload.put("arena", true);
        }

        if (req.getModel() != null && !req.getModel().isEmpty()) {
            payload.put("model", req.getModel());
        }
        if (req.getTemperature() != null) {
            payload.put("temperature", req.getTemperature());
        }

        if (req.getChatHistory() != null && !req.getChatHistory().isEmpty()) {
            payload.put("chat_history", req.getChatHistory());
        } else if (req.getChatHistoryJson() != null && !req.getChatHistoryJson().isEmpty()) {
            try {
                List<Map<String, Object>> parsedHistory = objectMapper.readValue(
                        req.getChatHistoryJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
                payload.put("chat_history", parsedHistory);
            } catch (Exception e) {
                logger.warn("Failed to parse chatHistoryJson", e);
            }
        }

        if (file != null && !file.isEmpty()) {
            payload.put("file_name", file.getOriginalFilename());
            payload.put("file_mime_type", file.getContentType());
            payload.put("file_base64", Base64.getEncoder().encodeToString(file.getBytes()));
        }

        return payload;
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