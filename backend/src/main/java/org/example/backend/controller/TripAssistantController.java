package org.example.backend.controller;

import org.example.backend.dto.AgentChatRequest;
import org.example.backend.dto.ApiResponse;
import org.example.backend.service.TripAssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
@CrossOrigin(origins = "*")
public class TripAssistantController {

    @Autowired
    private TripAssistantService tripAssistantService;

    /**
     * Legacy synchronous endpoint (backward compatible).
     */
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> chat(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        if (query == null || query.trim().isEmpty()) {
            return ApiResponse.error("query required");
        }
        Map<String, Object> result = tripAssistantService.generateTripPlan(query.trim(), file, userId);
        return ApiResponse.success(result);
    }

    /**
     * SSE streaming endpoint: proxies the Python agent's SSE stream.
     * Returns text/event-stream for real-time agent thinking/acting visualization.
     * Accepts model and temperature for per-request LLM configuration.
     */
    @PostMapping(value = "/chat/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter chatStream(
            AgentChatRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data(
                        Map.of("type", "error", "content", "query required")));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
        request.setQuery(request.getQuery().trim());
        return tripAssistantService.streamAgentChat(request, file);
    }
}