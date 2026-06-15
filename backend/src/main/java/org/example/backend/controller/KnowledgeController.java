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

    @GetMapping("/documents")
    public ApiResponse<Map<String, Object>> listDocuments() {
        try {
            return ApiResponse.success(knowledgeService.listDocuments());
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/documents/{docId}")
    public ApiResponse<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        try {
            return ApiResponse.success(knowledgeService.deleteDocument(docId));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/documents/upload")
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String fileName = body.get("fileName");
        String fileBase64 = body.get("fileBase64");
        String sourceType = body.get("sourceType");
        if (title == null || title.isBlank()) {
            return ApiResponse.error("title is required");
        }
        if (fileName == null || fileName.isBlank()) {
            return ApiResponse.error("fileName is required");
        }
        if (fileBase64 == null || fileBase64.isBlank()) {
            return ApiResponse.error("fileBase64 is required");
        }
        try {
            return ApiResponse.success(knowledgeService.uploadDocument(title, fileName, fileBase64, sourceType));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
