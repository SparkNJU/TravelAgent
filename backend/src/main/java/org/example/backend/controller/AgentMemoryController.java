package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.entity.AgentMemory;
import org.example.backend.service.AgentMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memories")
@CrossOrigin(origins = "*")
public class AgentMemoryController {

    @Autowired
    private AgentMemoryService memoryService;

    @GetMapping
    public ApiResponse<List<AgentMemory>> listMemories(@RequestParam Long userId) {
        try {
            List<AgentMemory> memories = memoryService.getAllMemoriesForUser(userId);
            return ApiResponse.success(memories);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/active")
    public ApiResponse<List<AgentMemory>> listActiveMemories(@RequestParam Long userId) {
        try {
            List<AgentMemory> memories = memoryService.getActiveMemoriesForUser(userId);
            return ApiResponse.success(memories);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping
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

    @PutMapping("/{id}")
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

    @PutMapping("/{id}/toggle")
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

    @DeleteMapping("/{id}")
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
}
