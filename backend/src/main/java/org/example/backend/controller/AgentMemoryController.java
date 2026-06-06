package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.AgentMemorySyncRequest;
import org.example.backend.entity.AgentMemory;
import org.example.backend.service.AgentMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class AgentMemoryController {

    @Autowired
    private AgentMemoryService memoryService;

    // --- Local settings page CRUD APIs (Base path: /api/memories) ---

    @GetMapping("/api/memories")
    public ApiResponse<List<AgentMemory>> listMemories(@RequestParam Long userId) {
        try {
            List<AgentMemory> memories = memoryService.getAllMemoriesForUser(userId);
            return ApiResponse.success(memories);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/api/memories/active")
    public ApiResponse<List<AgentMemory>> listActiveMemories(@RequestParam Long userId) {
        try {
            List<AgentMemory> memories = memoryService.getActiveMemoriesForUser(userId);
            return ApiResponse.success(memories);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/api/memories")
    public ApiResponse<AgentMemory> createMemory(
            @RequestParam Long userId,
            @RequestBody AgentMemory memory) {
        try {
            if (memory.getContent() == null || memory.getContent().trim().isEmpty()) {
                return ApiResponse.error("记忆内容不能为空");
            }
            AgentMemory saved = memoryService.saveMemory(memory, userId);
            return ApiResponse.success(saved);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/api/memories/{id}")
    public ApiResponse<AgentMemory> updateMemory(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestBody AgentMemory memory) {
        try {
            AgentMemory updated = memoryService.updateMemory(id, memory, userId);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/api/memories/{id}/toggle")
    public ApiResponse<AgentMemory> toggleMemory(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam Boolean isEnabled) {
        try {
            AgentMemory toggled = memoryService.toggleMemoryStatus(id, isEnabled, userId);
            return ApiResponse.success(toggled);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/api/memories/{id}")
    public ApiResponse<Void> deleteMemory(
            @PathVariable Long id,
            @RequestParam Long userId) {
        try {
            memoryService.deleteMemory(id, userId);
            return ApiResponse.success(null, "删除成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

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
