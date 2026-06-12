package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.KnowledgeSyncTurnRequest;
import org.example.backend.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {
    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/sync-turn")
    public ApiResponse<Map<String, Object>> syncTurn(@RequestBody KnowledgeSyncTurnRequest request) {
        if ((request.getUserMessage() == null || request.getUserMessage().isBlank())
                && (request.getAssistantAnswer() == null || request.getAssistantAnswer().isBlank())) {
            return ApiResponse.error("userMessage or assistantAnswer required");
        }
        try {
            return ApiResponse.success(knowledgeService.syncTurn(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
