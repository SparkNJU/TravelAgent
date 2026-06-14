package org.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.entity.AgentMemoryChangeLog;
import org.example.backend.entity.AgentPublicKnowledge;
import org.example.backend.entity.User;
import org.example.backend.entity.UserAgentMemory;
import org.example.backend.dto.AgentMemorySyncRequest;
import org.example.backend.repository.AgentPublicKnowledgeRepository;
import org.example.backend.repository.AgentMemoryChangeLogRepository;
import org.example.backend.repository.UserAgentMemoryRepository;
import org.example.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);

    private static final Map<String, String> CATEGORY_MAP = new LinkedHashMap<>();
    static {
        CATEGORY_MAP.put("user_name", "个人信息");
        CATEGORY_MAP.put("username", "个人信息");
        CATEGORY_MAP.put("age", "个人信息");
        CATEGORY_MAP.put("gender", "个人信息");
        CATEGORY_MAP.put("phone", "个人信息");
        CATEGORY_MAP.put("email", "个人信息");
        CATEGORY_MAP.put("occupation", "个人信息");

        CATEGORY_MAP.put("travel_style", "旅游偏好");
        CATEGORY_MAP.put("budget", "旅游偏好");
        CATEGORY_MAP.put("travel_party_size", "旅游偏好");
        CATEGORY_MAP.put("destination", "旅游偏好");
        CATEGORY_MAP.put("departure_city", "旅游偏好");
        CATEGORY_MAP.put("accommodation_preference", "旅游偏好");
        CATEGORY_MAP.put("transport_preference", "旅游偏好");
        CATEGORY_MAP.put("pace_preference", "旅游偏好");
        CATEGORY_MAP.put("travel_frequency", "旅游偏好");

        CATEGORY_MAP.put("food_allergy", "口味偏好");
        CATEGORY_MAP.put("dietary_restriction", "口味偏好");
        CATEGORY_MAP.put("cuisine_preference", "口味偏好");
        CATEGORY_MAP.put("favorite_food", "口味偏好");
        CATEGORY_MAP.put("dislike_food", "口味偏好");
        CATEGORY_MAP.put("spice_tolerance", "口味偏好");
    }

    private static final String DEFAULT_CATEGORY = "其他";

    private static final List<String> CATEGORY_ORDER = List.of("个人信息", "旅游偏好", "口味偏好", "其他");

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
    // Memory sync (used by both Agent and Frontend)
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

        boolean isAgentSync = request.getTriggerQuery() != null && !request.getTriggerQuery().isBlank()
            || request.getConversationSummary() != null && !request.getConversationSummary().isBlank()
            || request.getSourceConversationId() != null;

        Optional<UserAgentMemory> existingMemoryOpt = userAgentMemoryRepository.findByUserId(userId);
        UserAgentMemory memory = existingMemoryOpt.orElse(null);
        boolean memoryChanged = false;

        // ========== AGENT SYNC ==========
        if (isAgentSync) {
            JsonNode newFactsNode = readJsonNode(request.getUserFactsJson());
            List<Map<String, Object>> newFacts = parseFactArray(
                newFactsNode != null ? newFactsNode.toString() : null);

            String existingMarkdown = memory != null ? memory.getMemoryMarkdown() : null;
            List<Map<String, Object>> oldFacts = parseMarkdownToFacts(existingMarkdown);
            Set<String> disabledKeys = parseDisabledKeys(memory != null ? memory.getMemoryJson() : null);

            List<Map<String, Object>> mergedFacts = mergeFactsByKey(oldFacts, newFacts);

            String markdown = buildAgentMemoryMarkdown(username, mergedFacts, request.getConversationSummary());
            if (memory == null) {
                memory = new UserAgentMemory();
                memory.setUserId(userId);
            }
            memory.setMemoryMarkdown(markdown);

            String cacheJson = buildMemoryJsonCache(mergedFacts, disabledKeys);
            memory.setMemoryJson(cacheJson);

            memory.setSourceConversationId(request.getSourceConversationId());
            memory.setSummarySource("conversation");
            if (request.getModelVersion() != null && !request.getModelVersion().isBlank()) {
                memory.setMemoryVersion(request.getModelVersion());
            }
            userAgentMemoryRepository.save(memory);
            memoryChanged = true;

        // ========== FRONTEND SYNC ==========
        } else {
            String frontendMarkdown = request.getMemoryMarkdown();
            if (frontendMarkdown != null && !frontendMarkdown.isBlank()) {
                if (memory == null) {
                    memory = new UserAgentMemory();
                    memory.setUserId(userId);
                }

                memory.setMemoryMarkdown(frontendMarkdown);

                Set<String> disabledKeys;
                if (request.getDisabledKeys() != null) {
                    disabledKeys = new HashSet<>(request.getDisabledKeys());
                } else {
                    disabledKeys = parseDisabledKeys(memory.getMemoryJson());
                }

                List<Map<String, Object>> facts = parseMarkdownToFacts(frontendMarkdown);
                memory.setMemoryJson(buildMemoryJsonCache(facts, disabledKeys));

                memory.setSourceConversationId(request.getSourceConversationId());
                memory.setSummarySource("conversation");
                userAgentMemoryRepository.save(memory);
                memoryChanged = true;
            }
        }

        int knowledgeCount = upsertPublicKnowledge(request);

        if (memoryChanged) {
            agentMemoryChangeLogRepository.save(buildChangeLog(
                    userId,
                    "user",
                    "user_agent_memory",
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

    
    private List<Map<String, Object>> parseMarkdownToFacts(String markdown) {
        List<Map<String, Object>> facts = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) return facts;

        String[] sections = markdown.split("(?m)^## ");
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;

            String[] lines = section.split("\n");
            String category = lines[0].trim();


            if (category.equals("用户") || category.equals("对话摘要")
                || category.equals("可复用公共知识") || category.startsWith("#")) {
                continue;
            }

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // key: value pairs with optional evidence
                Pattern pairPattern = Pattern.compile("^-\\s*(.+?)\\s*:\\s*(.+?)(?:（证据:\\s*(.+?)）)?$");
                Matcher pairMatcher = pairPattern.matcher(line);
                if (pairMatcher.find()) {
                    String key = pairMatcher.group(1).trim();
                    String value = pairMatcher.group(2).trim();
                    String evidence = pairMatcher.group(3) != null ? pairMatcher.group(3).trim() : "";
                    Map<String, Object> fact = new HashMap<>();
                    fact.put("key", key);
                    fact.put("value", value);
                    fact.put("evidence", evidence);
                    fact.put("confidence", 0.8);
                    fact.put("category", category);
                    facts.add(fact);
                    continue;
                }

                // value-only lines 
                Pattern valueOnlyPattern = Pattern.compile("^-\\s*(.+)$");
                Matcher valueOnlyMatcher = valueOnlyPattern.matcher(line);
                if (valueOnlyMatcher.find()) {
                    String value = valueOnlyMatcher.group(1).trim();
                    if (!value.equals("暂无")) {
                        Map<String, Object> fact = new HashMap<>();
                        fact.put("key", "");
                        fact.put("value", value);
                        fact.put("evidence", "");
                        fact.put("confidence", 0.8);
                        fact.put("category", category);
                        facts.add(fact);
                    }
                }
            }
        }
        return facts;
    }

    private String buildAgentMemoryMarkdown(
            String username,
            List<Map<String, Object>> facts,
            String conversationSummary) {
        List<String> lines = new ArrayList<>();
        lines.add("# AGENT.md");
        lines.add("");

        lines.add("## 用户");
        lines.add("- username: " + (username != null ? username : "未知"));
        lines.add("");

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) {
            grouped.put(cat, new ArrayList<>());
        }
        for (Map<String, Object> fact : facts) {
            String key = (String) fact.getOrDefault("key", "");
            String storedCategory = (String) fact.getOrDefault("category", "");
            String category = storedCategory.isBlank() ? getCategory(key) : storedCategory;
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(fact);
        }

        boolean hasAnyFact = false;
        for (String category : CATEGORY_ORDER) {
            List<Map<String, Object>> catFacts = grouped.getOrDefault(category, Collections.emptyList());
            if (catFacts.isEmpty()) continue;
            hasAnyFact = true;
            lines.add("## " + category);
            for (Map<String, Object> fact : catFacts) {
                String key = (String) fact.getOrDefault("key", "");
                String value = (String) fact.getOrDefault("value", "");
                String evidence = (String) fact.getOrDefault("evidence", "");
                if (key.isBlank()) {
                    lines.add("- " + value);
                } else {
                    lines.add("- " + key + ": " + value
                            + (evidence.isBlank() ? "" : "（证据: " + evidence + "）"));
                }
            }
            lines.add("");
        }

        if (!hasAnyFact) {
            lines.add("## 个人信息");
            lines.add("- 暂无");
            lines.add("");
        }

        lines.add("## 对话摘要");
        lines.add(conversationSummary != null && !conversationSummary.isBlank()
                ? conversationSummary : "暂无");

        return String.join("\n", lines);
    }

    private String buildMemoryJsonCache(List<Map<String, Object>> facts, Set<String> disabledKeys) {
        try {
            Map<String, Object> cache = new LinkedHashMap<>();
            cache.put("disabledKeys", disabledKeys != null ? new ArrayList<>(disabledKeys) : new ArrayList<>());

            List<Map<String, Object>> cards = new ArrayList<>();
            long id = 1;
            for (Map<String, Object> fact : facts) {
                String key = (String) fact.getOrDefault("key", "");
                String value = (String) fact.getOrDefault("value", "");
                String storedCategory = (String) fact.getOrDefault("category", "");
                String category = storedCategory.isBlank() ? getCategory(key) : storedCategory;
                String content = key.isBlank() ? value : (key + ": " + value);
                boolean isEnabled = disabledKeys == null || !disabledKeys.contains(key);

                Map<String, Object> card = new LinkedHashMap<>();
                card.put("id", id++);
                card.put("key", key);
                card.put("content", content);
                card.put("category", category);
                card.put("isEnabled", isEnabled);
                cards.add(card);
            }
            cache.put("cards", cards);
            return objectMapper.writeValueAsString(cache);
        } catch (Exception e) {
            log.warn("Failed to build memoryJson cache: {}", e.getMessage());
            return "{\"disabledKeys\":[],\"cards\":[]}";
        }
    }

    /**
     * Parse disabledKeys from existing memoryJson cache.
     */
    private Set<String> parseDisabledKeys(String memoryJson) {
        Set<String> keys = new HashSet<>();
        if (memoryJson == null || memoryJson.isBlank()) return keys;
        try {
            JsonNode root = objectMapper.readTree(memoryJson);
            if (root.has("disabledKeys") && root.get("disabledKeys").isArray()) {
                for (JsonNode item : root.get("disabledKeys")) {
                    keys.add(item.asText());
                }
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return keys;
    }

    private String getCategory(String key) {
        return CATEGORY_MAP.getOrDefault(key, DEFAULT_CATEGORY);
    }

    private List<Map<String, Object>> mergeFactsByKey(
            List<Map<String, Object>> oldFacts,
            List<Map<String, Object>> newFacts) {
        Map<String, Map<String, Object>> factMap = new LinkedHashMap<>();
        for (Map<String, Object> fact : oldFacts) {
            String key = (String) fact.getOrDefault("key", "fact_" + factMap.size());
            factMap.putIfAbsent(key, fact);
        }
        for (Map<String, Object> fact : newFacts) {
            String key = (String) fact.getOrDefault("key", "fact_" + factMap.size());
            factMap.put(key, fact); // new overwrites old
        }
        return new ArrayList<>(factMap.values());
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
