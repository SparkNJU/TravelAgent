package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.AgentMemorySyncRequest;
import org.example.backend.service.AgentMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class AgentMemoryController {

    @Autowired
    private AgentMemoryService memoryService;

    // --- Remote agent auto-sync APIs (Base path: /api/agent/memory) ---

    @PostMapping("/api/agent/memory/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sync(@RequestBody AgentMemorySyncRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryService.syncMemory(request), "记忆已同步"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("记忆同步失败"));
        }
    }

    @GetMapping("/api/agent/memory/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> latest(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(memoryService.getLatestMemory(userId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("获取记忆失败"));
        }
    }
}
