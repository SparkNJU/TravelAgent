package org.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.dto.AgentChatRequest;
import org.example.backend.dto.ChatMessage;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.example.backend.entity.ChatConversation;
import org.example.backend.repository.ChatConversationRepository;
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

    @Value("${app.agent.compress-url:http://localhost:8000/api/agent/compress}")
    private String agentCompressUrl;

    private final ChatConversationRepository conversationRepository;

    // 流式接续：conversationId -> 已累积事件列表（用于回放）
    private final ConcurrentHashMap<Long, List<Map<String, Object>>> streamBuffers = new ConcurrentHashMap<>();
    // 流式接续：conversationId -> 实时事件队列（用于接续新事件）
    private final ConcurrentHashMap<Long, LinkedBlockingQueue<Map<String, Object>>> streamQueues = new ConcurrentHashMap<>();
    // 流式接续：conversationId -> 结束标记
    private final ConcurrentHashMap<Long, Boolean> streamEnded = new ConcurrentHashMap<>();

    public TripAssistantService(RestTemplate restTemplate, ChatConversationRepository conversationRepository) {
        this.restTemplate = restTemplate;
        this.conversationRepository = conversationRepository;
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
     * Accumulates all events into memory buffers for reconnection support.
     */
    public SseEmitter streamAgentChat(AgentChatRequest req, MultipartFile file) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        Long convId = req.getConversationId();

        // 初始化流式缓冲
        if (convId != null) {
            streamBuffers.put(convId, new java.util.ArrayList<>());
            streamQueues.put(convId, new LinkedBlockingQueue<>());
            streamEnded.put(convId, false);
            // 标记对话为流式中
            try {
                ChatConversation conv = conversationRepository.findByIdAndUserId(convId, req.getUserId()).orElse(null);
                if (conv != null) {
                    conv.setIsStreaming(true);
                    conversationRepository.save(conv);
                }
            } catch (Exception e) {
                logger.warn("Failed to set isStreaming=true for convId={}", convId, e);
            }
        }

        sseExecutor.execute(() -> {
            try {
                Map<String, Object> payload = buildAgentPayload(req, file);
                String jsonBody = objectMapper.writeValueAsString(payload);
                logger.info("SSE stream started: userId={}, mode={}, model={}, query={}, convId={}",
                        req.getUserId(), req.getMode(), req.getModel(), req.getQuery(), convId);

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
                    cleanupStream(convId);
                    return;
                }

                StringBuilder accumulatedAnswer = new StringBuilder();
                StringBuilder accumulatedPlan = new StringBuilder();
                List<Map<String, Object>> allEvents = new java.util.ArrayList<>();
                int eventCount = 0;

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
                                try {
                                    emitter.send(SseEmitter.event().name("done").data(
                                            Map.of("type", "done", "content", "")));
                                } catch (IOException ignored) {}
                                break;
                            }
                            // 解析并累积所有事件
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> evt = objectMapper.readValue(data, Map.class);
                                String type = (String) evt.get("type");
                                String content = evt.get("content") != null ? evt.get("content").toString() : "";

                                if ("answer".equals(type)) {
                                    accumulatedAnswer.append(content);
                                } else if ("plan".equals(type)) {
                                    accumulatedPlan.append(content);
                                }

                                // 保存所有非 done/token_status 事件
                                if (type != null && !"done".equals(type) && !"token_status".equals(type)) {
                                    Map<String, Object> eventRecord = new LinkedHashMap<>();
                                    eventRecord.put("type", type);
                                    eventRecord.put("content", content);
                                    if (evt.get("metadata") != null) {
                                        eventRecord.put("metadata", evt.get("metadata"));
                                    }
                                    allEvents.add(eventRecord);

                                    // 推入队列供 reconnect 使用
                                    if (convId != null) {
                                        LinkedBlockingQueue<Map<String, Object>> queue = streamQueues.get(convId);
                                        if (queue != null) {
                                            queue.offer(eventRecord);
                                        }
                                        List<Map<String, Object>> buffer = streamBuffers.get(convId);
                                        if (buffer != null) {
                                            buffer.add(eventRecord);
                                        }
                                    }
                                }

                                eventCount++;
                                // 每 5 个事件存一次 DB
                                if (eventCount % 5 == 0 && convId != null) {
                                    saveStreamResult(req, accumulatedAnswer.toString(), accumulatedPlan.toString(), allEvents, true);
                                }
                            } catch (Exception ignored) {}
                            logger.debug("SSE event: {}", data);
                            try {
                                emitter.send(SseEmitter.event().name("agent").data(data));
                            } catch (IOException e) {
                                // 前端断开，但继续读 Agent
                                logger.warn("SSE client disconnected, continuing Agent read: userId={}, convId={}", req.getUserId(), convId);
                            }
                        }
                    }
                }

                // 流式结束，最终存一次
                logger.info("SSE stream loop ended: userId={}, events={}, answerLen={}, convId={}",
                        req.getUserId(), allEvents.size(), accumulatedAnswer.length(), convId);
                saveStreamResult(req, accumulatedAnswer.toString(), accumulatedPlan.toString(), allEvents, false);

                // 标记流式结束
                if (convId != null) {
                    streamEnded.put(convId, true);
                    // 向队列推入结束标记
                    LinkedBlockingQueue<Map<String, Object>> queue = streamQueues.get(convId);
                    if (queue != null) {
                        queue.offer(Map.of("type", "__stream_end__", "content", ""));
                    }
                }

                logger.info("SSE stream completed: userId={}, convId={}", req.getUserId(), convId);
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            } catch (IOException e) {
                logger.warn("SSE client disconnected: userId={}, reason: {}", req.getUserId(), e.getMessage());
                try { emitter.complete(); } catch (Exception ignored) {}
            } catch (Exception e) {
                logger.error("Agent SSE proxy error: userId={}", req.getUserId(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            Map.of("type", "error", "content", e.getMessage())));
                } catch (Exception ignored) {}
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            } finally {
                // 延迟清理缓冲（给 reconnect 留时间读取）
                if (convId != null) {
                    sseExecutor.execute(() -> {
                        try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}
                        cleanupStream(convId);
                    });
                }
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> emitter.complete());

        return emitter;
    }

    /**
     * SSE reconnect endpoint: replays buffered events then streams new ones.
     */
    public SseEmitter reconnectStream(Long conversationId, Long userId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        sseExecutor.execute(() -> {
            try {
                List<Map<String, Object>> buffer = streamBuffers.get(conversationId);
                LinkedBlockingQueue<Map<String, Object>> queue = streamQueues.get(conversationId);
                Boolean ended = streamEnded.get(conversationId);

                if (buffer == null || queue == null) {
                    // 流式已结束或不存在
                    logger.info("Reconnect: no active stream for convId={}", conversationId);
                    emitter.send(SseEmitter.event().name("done").data(
                            Map.of("type", "done", "content", "")));
                    emitter.complete();
                    return;
                }

                logger.info("Reconnect: replaying {} buffered events for convId={}", buffer.size(), conversationId);

                // 回放缓冲中的已有事件
                for (Map<String, Object> event : buffer) {
                    try {
                        emitter.send(SseEmitter.event().name("agent").data(objectMapper.writeValueAsString(event)));
                    } catch (IOException e) {
                        logger.warn("Reconnect: client disconnected during replay, convId={}", conversationId);
                        return;
                    }
                }

                // 如果流式已结束，发送 done
                if (Boolean.TRUE.equals(ended)) {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(
                                Map.of("type", "done", "content", "")));
                    } catch (IOException ignored) {}
                    emitter.complete();
                    return;
                }

                // 从队列读取新事件（实时接续）
                logger.info("Reconnect: switching to live queue for convId={}", conversationId);
                while (true) {
                    Map<String, Object> event = queue.poll(30, TimeUnit.SECONDS);
                    if (event == null) {
                        // 超时，可能流式已结束
                        logger.info("Reconnect: queue timeout for convId={}", conversationId);
                        break;
                    }
                    if ("__stream_end__".equals(event.get("type"))) {
                        // 流式结束标记
                        try {
                            emitter.send(SseEmitter.event().name("done").data(
                                    Map.of("type", "done", "content", "")));
                        } catch (IOException ignored) {}
                        break;
                    }
                    try {
                        emitter.send(SseEmitter.event().name("agent").data(objectMapper.writeValueAsString(event)));
                    } catch (IOException e) {
                        logger.info("Reconnect: client disconnected during live, convId={}", conversationId);
                        return;
                    }
                }

                emitter.complete();
            } catch (Exception e) {
                logger.error("Reconnect error: convId={}", conversationId, e);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> emitter.complete());

        return emitter;
    }

    private void cleanupStream(Long convId) {
        streamBuffers.remove(convId);
        streamQueues.remove(convId);
        streamEnded.remove(convId);
        logger.info("Stream buffer cleaned up: convId={}", convId);
    }

    private Map<String, Object> buildAgentPayload(AgentChatRequest req, MultipartFile file) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", req.getQuery());
        payload.put("user_id", req.getUserId());
        payload.put("mode", req.getMode());
        payload.put("generate_plan_first", req.isGeneratePlanFirst());
        if (req.isArena()) payload.put("arena", true);
        if (req.isForceCompress()) payload.put("force_compress", true);
        if (req.getModel() != null && !req.getModel().isEmpty()) payload.put("model", req.getModel());
        if (req.getTemperature() != null) payload.put("temperature", req.getTemperature());
        payload.put("web_search_enabled", req.isWebSearchEnabled());
        payload.put("knowledge_search_enabled", req.isKnowledgeSearchEnabled());

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

    /**
     * 流式过程中周期性保存 + 流式结束最终保存。
     * 将累积的 answer、plan、events 写入对话的最后一条 assistant 消息。
     */
    private void saveStreamResult(AgentChatRequest req, String answer, String plan,
                                  List<Map<String, Object>> events, boolean isStreaming) {
        try {
            Long convId = req.getConversationId();
            Long userId = req.getUserId();
            if (convId == null) return;

            ChatConversation conv = conversationRepository.findByIdAndUserId(convId, userId).orElse(null);
            if (conv == null) return;

            String existingMessages = conv.getMessagesJson();
            if (existingMessages == null || existingMessages.isEmpty()) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = objectMapper.readValue(existingMessages, List.class);

            // 找到最后一条 assistant 消息并更新
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, Object> msg = messages.get(i);
                if ("assistant".equals(msg.get("role"))) {
                    if (answer != null && !answer.isEmpty()) {
                        msg.put("content", answer);
                    }
                    if (plan != null && !plan.isEmpty()) {
                        msg.put("planContent", plan);
                    }
                    if (events != null && !events.isEmpty()) {
                        msg.put("events", events);
                    }
                    break;
                }
            }

            conv.setMessagesJson(objectMapper.writeValueAsString(messages));
            conv.setIsStreaming(isStreaming);
            conversationRepository.save(conv);
            logger.debug("Stream result saved: convId={}, isStreaming={}, events={}", convId, isStreaming,
                    events != null ? events.size() : 0);
        } catch (Exception e) {
            logger.error("Failed to save stream result: convId={}", req.getConversationId(), e);
        }
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

    public Map<String, Object> compressConversation(List<ChatMessage> chatHistory, Integer keepLast) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_history", chatHistory == null ? List.of() : chatHistory);
        if (keepLast != null) {
            payload.put("keep_last", keepLast);
        }

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(agentCompressUrl, entity, String.class);
            int status = response.getStatusCode().value();
            if (status >= 200 && status < 300) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.getBody(), Map.class);
                return data;
            }
            logger.warn("Agent compress error status: {}, body: {}", status, response.getBody());
            return Map.of(
                "summary", "",
                "compressed", false,
                "keep_last", keepLast,
                "error", "Agent returned HTTP " + status
            );
        } catch (Exception e) {
            return Map.of(
                "summary", "",
                "compressed", false,
                "keep_last", keepLast,
                "error", e.getMessage()
            );
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