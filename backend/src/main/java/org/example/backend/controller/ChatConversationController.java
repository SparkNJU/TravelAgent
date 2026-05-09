package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.entity.ChatConversation;
import org.example.backend.repository.ChatConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "*")
public class ChatConversationController {

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listConversations(
            @RequestParam Long userId) {
        List<ChatConversation> conversations =
                chatConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<Map<String, Object>> result = conversations.stream()
                .map(this::toSummary)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getConversation(
            @PathVariable Long id,
            @RequestParam Long userId) {
        ChatConversation conv = chatConversationRepository.findByIdAndUserId(id, userId)
                .orElse(null);
        if (conv == null) {
            return ApiResponse.error("对话不存在");
        }
        return ApiResponse.success(toDetail(conv));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> saveConversation(
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String title = (String) body.getOrDefault("title", "新对话");
        String messagesJson = (String) body.getOrDefault("messagesJson", "[]");
        String resultJson = (String) body.get("resultJson");

        // Check if this is an update (id provided)
        Object idObj = body.get("id");
        ChatConversation conv;
        if (idObj != null) {
            Long id = Long.valueOf(idObj.toString());
            conv = chatConversationRepository.findByIdAndUserId(id, userId).orElse(null);
            if (conv == null) {
                conv = new ChatConversation();
                conv.setUserId(userId);
            }
        } else {
            conv = new ChatConversation();
            conv.setUserId(userId);
        }

        conv.setTitle(title);
        conv.setMessagesJson(messagesJson);
        conv.setResultJson(resultJson);
        conv = chatConversationRepository.save(conv);

        return ApiResponse.success(toDetail(conv));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateConversation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        ChatConversation conv = chatConversationRepository.findByIdAndUserId(id, userId)
                .orElse(null);
        if (conv == null) {
            return ApiResponse.error("对话不存在");
        }

        if (body.containsKey("title")) conv.setTitle((String) body.get("title"));
        if (body.containsKey("messagesJson")) conv.setMessagesJson((String) body.get("messagesJson"));
        if (body.containsKey("resultJson")) conv.setResultJson((String) body.get("resultJson"));
        conv = chatConversationRepository.save(conv);

        return ApiResponse.success(toDetail(conv));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable Long id,
            @RequestParam Long userId) {
        ChatConversation conv = chatConversationRepository.findByIdAndUserId(id, userId)
                .orElse(null);
        if (conv == null) {
            return ApiResponse.error("对话不存在");
        }
        chatConversationRepository.delete(conv);
        return ApiResponse.success(null);
    }

    private Map<String, Object> toSummary(ChatConversation conv) {
        return Map.of(
                "id", conv.getId(),
                "title", conv.getTitle() != null ? conv.getTitle() : "新对话",
                "createdAt", conv.getCreatedAt().toString(),
                "updatedAt", conv.getUpdatedAt().toString()
        );
    }

    private Map<String, Object> toDetail(ChatConversation conv) {
        Map<String, Object> map = new java.util.HashMap<>(toSummary(conv));
        map.put("messagesJson", conv.getMessagesJson());
        map.put("resultJson", conv.getResultJson());
        return map;
    }
}
