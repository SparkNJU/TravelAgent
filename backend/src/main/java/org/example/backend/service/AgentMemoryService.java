package org.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.backend.entity.AgentMemory;
import org.example.backend.entity.AgentMemoryChangeLog;
import org.example.backend.entity.AgentPublicKnowledge;
import org.example.backend.entity.User;
import org.example.backend.entity.UserAgentMemory;
import org.example.backend.dto.AgentMemorySyncRequest;
import org.example.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);

    @Autowired
    private AgentMemoryRepository memoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAgentMemoryRepository userAgentMemoryRepository;

    @Autowired
    private AgentPublicKnowledgeRepository agentPublicKnowledgeRepository;

    @Autowired
    private AgentMemoryChangeLogRepository agentMemoryChangeLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================
    // Local CRUD methods for Settings View UI
    // ==========================================

    @PostConstruct
    public void seedMemories() {
        try {
            log.info("Checking and seeding default user memories...");
            // Seed for standard user with ID 1
            Long userId = 1L;
            List<AgentMemory> existing = memoryRepository.findAllByUserId(userId);
            if (existing.isEmpty()) {
                memoryRepository.save(new AgentMemory("偏爱深度游和慢节奏，每天规划的景点不要超过3个", true, userId));
                memoryRepository.save(new AgentMemory("对海鲜过敏，在推荐美食时请避免推荐海鲜餐馆", true, userId));
                memoryRepository.save(new AgentMemory("出行预算偏向经济型，住宿优先考虑舒适型民宿或3星级酒店", true, userId));
                log.info("Seeded default memories for user 1");
            }
        } catch (Exception e) {
            log.error("Failed to seed default user memories", e);
        }
    }

    public List<AgentMemory> getAllMemoriesForUser(Long userId) {
        return memoryRepository.findAllByUserId(userId);
    }

    public List<AgentMemory> getActiveMemoriesForUser(Long userId) {
        return memoryRepository.findActiveByUserId(userId);
    }

    public Optional<AgentMemory> getMemoryById(Long id) {
        return memoryRepository.findById(id);
    }

    public AgentMemory saveMemory(AgentMemory memory, Long userId) {
        memory.setUserId(userId);
        return memoryRepository.save(memory);
    }

    public AgentMemory updateMemory(Long id, AgentMemory updatedData, Long userId) {
        AgentMemory existing = memoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该记忆不存在"));

        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改他人的个性化记忆");
        }

        if (updatedData.getContent() != null) {
            existing.setContent(updatedData.getContent());
        }
        if (updatedData.getIsEnabled() != null) {
            existing.setIsEnabled(updatedData.getIsEnabled());
        }

        return memoryRepository.save(existing);
    }

    public void deleteMemory(Long id, Long userId) {
        AgentMemory existing = memoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该记忆不存在"));

        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人的个性化记忆");
        }

        memoryRepository.delete(existing);
    }

    public AgentMemory toggleMemoryStatus(Long id, Boolean isEnabled, Long userId) {
        AgentMemory existing = memoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该记忆不存在"));

        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改他人的个性化记忆状态");
        }

        existing.setIsEnabled(isEnabled);
        return memoryRepository.save(existing);
    }

    // ==========================================
    // Remote automated memory sync methods
    // ==========================================

    @Transactional
    public Map<String, Object> syncMemory(AgentMemorySyncRequest request) {
        Long userId = request.getUserId();
        if (userId == null) {
            throw new RuntimeException("userId required");
        }
        User user = userRepository.findById(userId).orElse(null);
        String username = user != null && user.getUsername() != null
            ? user.getUsername()
            : "guest-" + userId;

        JsonNode newFactsNode = readJsonNode(request.getUserFactsJson());
        String normalizedFacts = newFactsNode != null ? newFactsNode.toString() : null;
        boolean hasAnyMemoryPayload = hasAnyMemoryPayload(request, normalizedFacts);

        Optional<UserAgentMemory> existingMemoryOpt = userAgentMemoryRepository.findByUserId(userId);
        UserAgentMemory memory = existingMemoryOpt.orElse(null);
        boolean memoryChanged = false;

        String existingFacts = memory != null ? normalizeJson(memory.getMemoryJson()) : null;
        boolean factsChanged = normalizedFacts != null && !normalizedFacts.isBlank()
                && (existingFacts == null || !existingFacts.equals(normalizedFacts));
        boolean isAgentSync = request.getTriggerQuery() != null && !request.getTriggerQuery().isBlank()
            || request.getConversationSummary() != null && !request.getConversationSummary().isBlank()
            || request.getSourceConversationId() != null;

        if (memory == null && hasAnyMemoryPayload) {
            memory = new UserAgentMemory();
            memory.setUserId(userId);
        }

        if (memory != null && isAgentSync) {
            List<Map<String, Object>> newFacts = parseFactArray(normalizedFacts);
            String currentMarkdown = memory.getMemoryMarkdown();
            if (currentMarkdown == null) currentMarkdown = "";
            StringBuilder mdBuilder = new StringBuilder(currentMarkdown);
            if (!currentMarkdown.isBlank() && !currentMarkdown.endsWith("\n")) {
                mdBuilder.append("\n");
            }
            boolean hasNewContent = false;
            for (Map<String, Object> fact : newFacts) {
                String value = (String) fact.get("value");
                if (value == null || value.isBlank()) continue;
                if (!currentMarkdown.contains(value)) {
                    if (!hasNewContent) {
                        mdBuilder.append("\n### 对话提取\n");
                        hasNewContent = true;
                    }
                    mdBuilder.append("- ").append(value).append("\n");
                }
            }
            if (hasNewContent || existingMemoryOpt.isEmpty()) {
                memory.setMemoryMarkdown(mdBuilder.toString().trim());
                memory.setSourceConversationId(request.getSourceConversationId());
                memory.setSummarySource("conversation");
                if (request.getModelVersion() != null && !request.getModelVersion().isBlank()) {
                    memory.setMemoryVersion(request.getModelVersion());
                }
                userAgentMemoryRepository.save(memory);
                memoryChanged = true;
            }
        } else if (memory != null && (factsChanged || existingMemoryOpt.isEmpty())) {
            String frontendMarkdown = request.getMemoryMarkdown();
            if (frontendMarkdown != null && !frontendMarkdown.isBlank()) {
                memory.setMemoryMarkdown(frontendMarkdown);
            } else {
                memory.setMemoryMarkdown(buildMemoryMarkdown(username, request));
            }
            memory.setMemoryJson(normalizedFacts != null && !normalizedFacts.isBlank() ? normalizedFacts : existingFacts);
            memory.setSourceConversationId(request.getSourceConversationId());
            memory.setSummarySource("conversation");
            if (request.getModelVersion() != null && !request.getModelVersion().isBlank()) {
                memory.setMemoryVersion(request.getModelVersion());
            }
            userAgentMemoryRepository.save(memory);
            memoryChanged = true;
        }

        int knowledgeCount = upsertPublicKnowledge(request);

        if (memoryChanged) {
            agentMemoryChangeLogRepository.save(buildChangeLog(
                    userId,
                    "user",
                    memory.getId() != null ? "user_agent_memory" : "user_agent_memory",
                    request,
                    existingMemoryOpt.map(UserAgentMemory::getMemoryJson).orElse(null),
                    memory.getMemoryJson()
            ));
        }

        if (knowledgeCount > 0) {
            agentMemoryChangeLogRepository.save(buildChangeLog(
                    userId,
                    "public",
                    "agent_public_knowledge",
                    request,
                    null,
                    request.getPublicKnowledgeJson()
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("userName", username);
        result.put("memoryUpdated", memoryChanged);
        result.put("publicKnowledgeCount", knowledgeCount);
        result.put("memoryId", memory != null ? memory.getId() : null);
        return result;
    }

    public Map<String, Object> getLatestMemory(Long userId) {
        UserAgentMemory memory = userAgentMemoryRepository.findByUserId(userId).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("memory", memory);
        return result;
    }

    private int upsertPublicKnowledge(AgentMemorySyncRequest request) {
        JsonNode nodes = readJsonNode(request.getPublicKnowledgeJson());
        if (nodes == null || !nodes.isArray()) {
            return 0;
        }

        int count = 0;
        for (JsonNode node : nodes) {
            String knowledgeKey = text(node, "knowledgeKey", null);
            String knowledgeTitle = text(node, "knowledgeTitle", knowledgeKey);
            String knowledgeContent = text(node, "knowledgeContent", "");
            if (knowledgeKey == null || knowledgeKey.isBlank() || knowledgeContent.isBlank()) {
                continue;
            }

            AgentPublicKnowledge knowledge = agentPublicKnowledgeRepository.findByKnowledgeKey(knowledgeKey)
                    .orElseGet(AgentPublicKnowledge::new);
            knowledge.setKnowledgeKey(knowledgeKey);
            knowledge.setKnowledgeTitle(knowledgeTitle != null ? knowledgeTitle : knowledgeKey);
            knowledge.setKnowledgeContent(knowledgeContent);
            knowledge.setKnowledgeJson(node.toString());
            knowledge.setKnowledgeScope(text(node, "knowledgeScope", "global"));
            knowledge.setContributorUserId(request.getUserId());
            knowledge.setSourceConversationId(request.getSourceConversationId());

            BigDecimal confidence = decimal(node, "confidenceScore", new BigDecimal("0.80"));
            knowledge.setConfidenceScore(confidence);

            Integer usageCount = knowledge.getUsageCount();
            knowledge.setUsageCount(usageCount == null ? 1 : usageCount + 1);
            agentPublicKnowledgeRepository.save(knowledge);
            count++;
        }

        return count;
    }

    private AgentMemoryChangeLog buildChangeLog(
            Long userId,
            String scope,
            String targetKey,
            AgentMemorySyncRequest request,
            String beforeSnapshot,
            String afterSnapshot
    ) {
        AgentMemoryChangeLog log = new AgentMemoryChangeLog();
        log.setUserId(userId);
        log.setMemoryScope(scope);
        log.setChangeType(beforeSnapshot == null ? "insert" : "update");
        log.setTargetKey(targetKey);
        log.setSourceConversationId(request.getSourceConversationId());
        log.setTriggerQuery(request.getTriggerQuery());
        log.setBeforeSnapshot(beforeSnapshot);
        log.setAfterSnapshot(afterSnapshot);
        log.setTokenInput(request.getTokenInput());
        log.setTokenOutput(request.getTokenOutput());
        log.setModelVersion(request.getModelVersion());
        return log;
    }

    private String buildMemoryMarkdown(String username, AgentMemorySyncRequest request) {
        List<String> lines = new ArrayList<>();
        lines.add("# AGENT.md");
        lines.add("");
        lines.add("## 用户");
        lines.add("- username: " + username);
        lines.add("");
        lines.add("## 用户画像事实");
        JsonNode facts = readJsonNode(request.getUserFactsJson());
        if (facts != null && facts.isArray() && facts.size() > 0) {
            for (JsonNode fact : facts) {
                String key = text(fact, "key", "fact");
                String value = text(fact, "value", "");
                String evidence = text(fact, "evidence", "");
                lines.add("- " + key + ": " + value + (evidence.isBlank() ? "" : "（证据: " + evidence + "）"));
            }
        } else {
            lines.add("- 暂无");
        }
        lines.add("");
        lines.add("## 对话摘要");
        String summary = request.getConversationSummary();
        lines.add(summary != null && !summary.isBlank() ? summary : "暂无");
        lines.add("");
        lines.add("## 可复用公共知识");
        JsonNode publicKnowledge = readJsonNode(request.getPublicKnowledgeJson());
        if (publicKnowledge != null && publicKnowledge.isArray() && publicKnowledge.size() > 0) {
            for (JsonNode item : publicKnowledge) {
                String title = text(item, "knowledgeTitle", text(item, "knowledgeKey", "知识"));
                String content = text(item, "knowledgeContent", "");
                lines.add("- " + title + ": " + content);
            }
        } else {
            lines.add("- 暂无");
        }
        return String.join("\n", lines);
    }

    private JsonNode readJsonNode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeJson(String raw) {
        JsonNode node = readJsonNode(raw);
        return node != null ? node.toString() : null;
    }

    private boolean hasAnyMemoryPayload(AgentMemorySyncRequest request, String normalizedFacts) {
        if (normalizedFacts != null && !normalizedFacts.isBlank()) {
            return true;
        }
        if (request.getConversationSummary() != null && !request.getConversationSummary().isBlank()) {
            return true;
        }
        if (request.getMemoryMarkdown() != null && !request.getMemoryMarkdown().isBlank()) {
            return true;
        }
        JsonNode publicKnowledge = readJsonNode(request.getPublicKnowledgeJson());
        return publicKnowledge != null && publicKnowledge.isArray() && publicKnowledge.size() > 0;
    }

    private List<Map<String, Object>> parseCardArray(String json) {
        List<Map<String, Object>> cards = new ArrayList<>();
        if (json == null || json.isBlank()) return cards;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    if (!item.has("content")) continue;
                    Map<String, Object> card = new HashMap<>();
                    card.put("id", item.has("id") ? item.get("id").asLong() : System.currentTimeMillis());
                    card.put("content", item.get("content").asText());
                    card.put("isEnabled", item.has("isEnabled") ? item.get("isEnabled").asBoolean() : true);
                    card.put("createdAt", item.has("createdAt") ? item.get("createdAt").asText() : java.time.LocalDateTime.now().toString());
                    card.put("updatedAt", item.has("updatedAt") ? item.get("updatedAt").asText() : java.time.LocalDateTime.now().toString());
                    cards.add(card);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse card array: {}", e.getMessage());
        }
        return cards;
    }

    private List<Map<String, Object>> parseFactArray(String json) {
        List<Map<String, Object>> facts = new ArrayList<>();
        if (json == null || json.isBlank()) return facts;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    if (!item.has("value")) continue;
                    Map<String, Object> fact = new HashMap<>();
                    fact.put("key", item.has("key") ? item.get("key").asText() : "fact");
                    fact.put("value", item.get("value").asText());
                    fact.put("evidence", item.has("evidence") ? item.get("evidence").asText() : "");
                    fact.put("confidence", item.has("confidence") ? item.get("confidence").asDouble() : 0.6);
                    facts.add(fact);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse fact array: {}", e.getMessage());
        }
        return facts;
    }

    private boolean mergeFactsIntoCardArray(List<Map<String, Object>> cards, List<Map<String, Object>> facts) {
        boolean appended = false;
        long baseId = System.currentTimeMillis();
        int counter = 0;
        for (Map<String, Object> fact : facts) {
            String value = (String) fact.get("value");
            if (value == null || value.isBlank()) continue;
            boolean exists = false;
            String valueNorm = value.replaceAll("\\s+", "").toLowerCase();
            for (Map<String, Object> card : cards) {
                String content = (String) card.get("content");
                if (content == null) continue;
                if (content.replaceAll("\\s+", "").toLowerCase().contains(valueNorm)
                    || valueNorm.contains(content.replaceAll("\\s+", "").toLowerCase())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                Map<String, Object> newCard = new HashMap<>();
                newCard.put("id", baseId + counter++);
                newCard.put("content", value);
                newCard.put("isEnabled", true);
                newCard.put("createdAt", java.time.LocalDateTime.now().toString());
                newCard.put("updatedAt", java.time.LocalDateTime.now().toString());
                cards.add(newCard);
                appended = true;
            }
        }
        return appended || facts.isEmpty();
    }

    private String buildUserPreferenceMarkdown(List<Map<String, Object>> cards) {
        List<String> lines = new ArrayList<>();
        lines.add("# 用户偏好记忆");
        lines.add("");
        for (Map<String, Object> card : cards) {
            boolean enabled = card.get("isEnabled") instanceof Boolean
                ? (Boolean) card.get("isEnabled") : true;
            String content = (String) card.get("content");
            if (content != null && !content.isBlank()) {
                lines.add((enabled ? "- " : "- ~~") + content + (enabled ? "" : "~~"));
            }
        }
        return String.join("\n", lines);
    }

    private String text(JsonNode node, String fieldName, String defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return defaultValue;
        }
        String value = field.asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private BigDecimal decimal(JsonNode node, String fieldName, BigDecimal defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return defaultValue;
        }
        try {
            return field.decimalValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
