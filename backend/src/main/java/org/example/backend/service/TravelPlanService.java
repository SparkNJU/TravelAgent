package org.example.backend.service;

import org.example.backend.dto.SavePlanRequest;
import org.example.backend.dto.TravelPlanRequest;
import org.example.backend.dto.TravelPlanResponse;
import org.example.backend.entity.PlanHighlight;
import org.example.backend.entity.TravelPlan;
import org.example.backend.entity.PlanActivity;
import org.example.backend.entity.ChatConversation;
import org.example.backend.repository.TravelPlanRepository;
import org.example.backend.repository.ChatConversationRepository;
import org.example.backend.repository.PlanActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TravelPlanService {

    private static final Logger logger = LoggerFactory.getLogger(TravelPlanService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private PlanActivityRepository planActivityRepository;

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private TripAssistantService tripAssistantService;

    /**
     * Generate and save a travel plan using AgentLLM
     * This is a mock implementation - integrate with real AgentLLM API later
     */
    public TravelPlanResponse generateTravelPlan(TravelPlanRequest request, Long userId) {
        // Create TravelPlan entity
        TravelPlan plan = new TravelPlan();
        plan.setUserId(userId);
        plan.setTitle("完美的" + request.getDestination() + "之旅");
        plan.setDestinationName(request.getDestination());
        plan.setDays(request.getDays());
        plan.setEstimatedBudget(BigDecimal.valueOf(request.getBudget()));
        plan.setAiConfidenceScore(BigDecimal.valueOf(0.92));
        plan.setInterests(request.getInterests());
        plan.setTravelStyle(request.getTravelStyle());
        plan.setStatus("draft");

        // First save the plan to get its ID
        TravelPlan savedPlan = travelPlanRepository.save(plan);

        // Generate mock activities
        List<PlanActivity> activities = new java.util.ArrayList<>();
        for (int i = 1; i <= request.getDays(); i++) {
            PlanActivity act = new PlanActivity();
            act.setPlanId(savedPlan.getId());
            act.setDayNumber(i);
            act.setActivityTime("09:00 - 12:00");
            act.setLocationName(request.getDestination() + "著名景点 " + i);
            act.setLatitude(BigDecimal.valueOf(30.0 + Math.random()));
            act.setLongitude(BigDecimal.valueOf(110.0 + Math.random()));
            act.setDescription("游览" + request.getDestination() + "的精选地标。");
            act.setTips("请提前预约门票。");
            act.setCost(BigDecimal.valueOf(100.0));
            activities.add(act);
        }
        savedPlan.getActivities().addAll(activities);

        // Add highlights
        List<String> highlightTexts = generateHighlights(request);
        java.util.Set<PlanHighlight> highlights = new java.util.HashSet<>();
        for (String text : highlightTexts) {
            PlanHighlight h = new PlanHighlight();
            h.setHighlightText(text);
            h.setPlanId(savedPlan.getId());
            highlights.add(h);
        }

        savedPlan.setHighlights(highlights);

        // Save highlights and activities
        TravelPlan finalPlan = travelPlanRepository.save(savedPlan);

        // Convert to response
        return convertToResponse(finalPlan);
    }

    /**
     * Get all plans for a user
     */
    public List<TravelPlanResponse> getUserPlans(Long userId) {
        return travelPlanRepository.findByUserId(userId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific plan
     */
    public TravelPlanResponse getPlanById(Long planId) {
        return travelPlanRepository.findById(planId)
                .map(this::convertToResponse)
                .orElse(null);
    }

    /**
     * Generate sample plans (for demonstration)
     */
    public List<TravelPlanResponse> getSamplePlans() {
        return Arrays.asList(
                new TravelPlanResponse(
                        1L,
                        "梦幻欧洲10日游",
                        "法国",
                        10,
                        createMockActivities(1L, "法国", 10),
                        24999,
                        0.95,
                        Arrays.asList("埃菲尔铁塔", "卢浮宫", "阿尔卑斯山", "威尼斯")),
                new TravelPlanResponse(
                        2L,
                        "亚洲美食之旅",
                        "泰国",
                        7,
                        createMockActivities(2L, "泰国", 7),
                        8999,
                        0.89,
                        Arrays.asList("大皇宫", "水上市场", "清迈古城")));
    }

    private List<PlanActivity> createMockActivities(Long planId, String dest, int days) {
        List<PlanActivity> list = new java.util.ArrayList<>();
        for (int i = 1; i <= days; i++) {
            PlanActivity act = new PlanActivity();
            act.setId(planId * 100 + i);
            act.setPlanId(planId);
            act.setDayNumber(i);
            act.setActivityTime("09:00");
            act.setLocationName(dest + "游览点 " + i);
            act.setLatitude(BigDecimal.valueOf(30.0 + i * 0.1));
            act.setLongitude(BigDecimal.valueOf(110.0 + i * 0.1));
            act.setDescription(dest + "第" + i + "天的精选行程描述。");
            act.setTips("推荐游览时间：3小时。");
            act.setCost(BigDecimal.valueOf(50.0));
            list.add(act);
        }
        return list;
    }

    /**
     * Generate itinerary based on request
     */
    private String generateItinerary(TravelPlanRequest request) {
        StringBuilder itinerary = new StringBuilder();
        itinerary.append("Day 1: 抵达").append(request.getDestination()).append("，入住酒店，熟悉当地环境。\n");

        for (int i = 2; i <= request.getDays(); i++) {
            itinerary.append("Day ").append(i).append(": ");
            if (request.getInterests() != null) {
                if (request.getInterests().contains("culture")) {
                    itinerary.append("参访文化景点，");
                }
                if (request.getInterests().contains("food")) {
                    itinerary.append("品尝本地美食，");
                }
                if (request.getInterests().contains("nature")) {
                    itinerary.append("游览自然风景，");
                }
                if (request.getInterests().contains("adventure")) {
                    itinerary.append("体验冒险活动，");
                }
            }
            itinerary.append("享受独特体验。\n");
        }

        itinerary.append("Day ").append(request.getDays()).append(": 返回。");
        return itinerary.toString();
    }

    /**
     * Generate highlights based on request
     */
    private List<String> generateHighlights(TravelPlanRequest request) {
        return Arrays.asList(
                "✨ " + request.getDestination() + "的标志性景点",
                "🍽️ 地道美食之旅",
                "📸 拍照圣地打卡",
                "🛍️ 特色购物体验",
                "🏨 精选住宿推荐");
    }

    /**
     * Save a travel plan to user's personal plans
     */
    public TravelPlanResponse savePlan(org.example.backend.dto.SavePlanRequest request) {
        // Create TravelPlan entity
        TravelPlan plan = new TravelPlan();
        plan.setUserId(request.getUserId());
        plan.setTitle(request.getTitle());
        plan.setDestinationName(request.getDestination());
        plan.setDays(request.getDays());
        plan.setEstimatedBudget(request.getBudget() != null ? BigDecimal.valueOf(request.getBudget()) : null);
        plan.setInterests(request.getInterests());
        plan.setTravelStyle(request.getTravelStyle());
        plan.setStatus("saved");
        plan.setAiConfidenceScore(BigDecimal.valueOf(0.90));

        // First save the plan to get its ID
        TravelPlan savedPlan = travelPlanRepository.save(plan);

        // Add activities
        if (request.getActivities() != null && !request.getActivities().isEmpty()) {
            List<PlanActivity> activities = request.getActivities();
            savedPlan.getActivities().clear();
            for (PlanActivity act : activities) {
                act.setPlanId(savedPlan.getId());
                savedPlan.getActivities().add(act);
            }
            savedPlan = travelPlanRepository.save(savedPlan);
        }

        // Add highlights
        if (request.getHighlights() != null && !request.getHighlights().isEmpty()) {
            java.util.Set<PlanHighlight> highlights = new java.util.HashSet<>();
            for (String text : request.getHighlights()) {
                PlanHighlight h = new PlanHighlight();
                h.setHighlightText(text);
                h.setPlanId(savedPlan.getId());
                highlights.add(h);
            }
            savedPlan.setHighlights(highlights);
            savedPlan = travelPlanRepository.save(savedPlan);
        }

        return convertToResponse(savedPlan);
    }

    /**
     * Update a plan
     */
    @Transactional
    public TravelPlanResponse updatePlan(Long planId, SavePlanRequest request) {
        return travelPlanRepository.findById(planId)
                .map(plan -> {
                    if (request.getTitle() != null) {
                        plan.setTitle(request.getTitle());
                    }
                    if (request.getDestination() != null) {
                        plan.setDestinationName(request.getDestination());
                    }
                    if (request.getDays() != null) {
                        plan.setDays(request.getDays());
                    }
                    if (request.getBudget() != null) {
                        plan.setEstimatedBudget(BigDecimal.valueOf(request.getBudget()));
                    }
                    if (request.getInterests() != null) {
                        plan.setInterests(request.getInterests());
                    }
                    if (request.getTravelStyle() != null) {
                        plan.setTravelStyle(request.getTravelStyle());
                    }
                    if (request.getActivities() != null) {
                        plan.getActivities().clear();
                        for (PlanActivity act : request.getActivities()) {
                            act.setId(null);
                            act.setPlanId(planId);
                            plan.getActivities().add(act);
                        }
                    }
                    return convertToResponse(travelPlanRepository.save(plan));
                })
                .orElse(null);
    }

    /**
     * Delete a plan
     */
    public boolean deletePlan(Long planId, Long userId) {
        return travelPlanRepository.findById(planId)
                .filter(plan -> plan.getUserId().equals(userId))
                .map(plan -> {
                    travelPlanRepository.delete(plan);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Convert TravelPlan entity to DTO
     */
    private TravelPlanResponse convertToResponse(TravelPlan plan) {
        List<String> highlightTexts = plan.getHighlights() != null ? plan.getHighlights().stream()
                .map(PlanHighlight::getHighlightText)
                .collect(Collectors.toList()) : Arrays.asList("景点体验", "美食品尝", "文化探索");

        return new TravelPlanResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getDestinationName(),
                plan.getDays(),
                plan.getActivities(),
                plan.getEstimatedBudget() != null ? plan.getEstimatedBudget().intValue() : 0,
                plan.getAiConfidenceScore() != null ? plan.getAiConfidenceScore().doubleValue() : 0.0,
                highlightTexts);
    }

    public TravelPlanResponse parseAndSaveConversation(Long conversationId) {
        try {
            // 1. Fetch conversation
            ChatConversation conv = chatConversationRepository.findById(conversationId).orElse(null);
            if (conv == null) {
                throw new RuntimeException("对话记录不存在 (id=" + conversationId + ")，请返回对话页重试");
            }

            // 2. Extract markdown content from messagesJson or resultJson
            String markdown = "";
            String destination = "未知";
            int days = 3;

            // Try reading resultJson first
            if (conv.getResultJson() != null && !conv.getResultJson().isEmpty()) {
                try {
                    Map<String, Object> resultObj = objectMapper.readValue(conv.getResultJson(), Map.class);
                    if (resultObj.containsKey("markdown")) {
                        markdown = (String) resultObj.get("markdown");
                    }
                    if (resultObj.containsKey("destination")) {
                        destination = (String) resultObj.get("destination");
                    }
                    if (resultObj.containsKey("days")) {
                        days = Integer.parseInt(resultObj.get("days").toString());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse resultJson in conversation {}", conversationId, e);
                }
            }

            // If markdown is still empty, look at the last assistant message in messagesJson
            if (markdown == null || markdown.isEmpty()) {
                if (conv.getMessagesJson() != null && !conv.getMessagesJson().isEmpty()) {
                    try {
                        List<Map<String, Object>> messages = objectMapper.readValue(
                            conv.getMessagesJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                        );
                        for (int i = messages.size() - 1; i >= 0; i--) {
                            Map<String, Object> msg = messages.get(i);
                            if ("assistant".equals(msg.get("role"))) {
                                markdown = (String) msg.get("content");
                                if (markdown != null && !markdown.isEmpty()) {
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse messagesJson in conversation {}", conversationId, e);
                    }
                }
            }

            if (markdown == null || markdown.isEmpty()) {
                throw new RuntimeException("未在对话记录中找到行程规划内容，请返回对话页确认 AI 已生成完整行程");
            }

            logger.info("Extracted markdown ({} chars) from conversation {}, calling Agent to parse...", markdown.length(), conversationId);

            // 3. Call python agent to parse the plan markdown into JSON activities with coordinates
            Map<String, Object> agentResult = tripAssistantService.parsePlanMarkdown(markdown, destination);
            if (agentResult == null) {
                throw new RuntimeException("Agent 行程解析服务暂时不可用，请确认 Agent 服务已启动");
            }

            // 4. Create TravelPlan and save
            TravelPlan plan = new TravelPlan();
            plan.setUserId(conv.getUserId());
            plan.setTitle((String) agentResult.getOrDefault("title", conv.getTitle() != null ? conv.getTitle() : "我的规划行程"));
            plan.setDestinationName((String) agentResult.getOrDefault("destination", destination));

            Object daysObj = agentResult.get("days");
            if (daysObj != null) {
                plan.setDays(Integer.parseInt(daysObj.toString()));
            } else {
                plan.setDays(days);
            }

            plan.setEstimatedBudget(BigDecimal.valueOf(1000.0)); // Default budget
            plan.setAiConfidenceScore(BigDecimal.valueOf(0.95));
            plan.setStatus("draft");

            TravelPlan savedPlan = travelPlanRepository.save(plan);

            // 5. Populate and save activities
            List<PlanActivity> activities = new java.util.ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> activitiesList = (List<Map<String, Object>>) agentResult.get("activities");
            if (activitiesList != null) {
                for (Map<String, Object> actMap : activitiesList) {
                    PlanActivity act = new PlanActivity();
                    act.setPlanId(savedPlan.getId());

                    Object dayNumObj = actMap.get("dayNumber");
                    act.setDayNumber(dayNumObj != null ? Integer.parseInt(dayNumObj.toString()) : 1);

                    act.setActivityTime((String) actMap.getOrDefault("activityTime", "全天"));
                    act.setLocationName((String) actMap.getOrDefault("locationName", "未知地点"));

                    Object latObj = actMap.get("latitude");
                    Object lngObj = actMap.get("longitude");
                    act.setLatitude(latObj != null ? BigDecimal.valueOf(Double.parseDouble(latObj.toString())) : BigDecimal.ZERO);
                    act.setLongitude(lngObj != null ? BigDecimal.valueOf(Double.parseDouble(lngObj.toString())) : BigDecimal.ZERO);

                    act.setDescription((String) actMap.getOrDefault("description", ""));
                    act.setTips((String) actMap.getOrDefault("tips", ""));

                    Object costObj = actMap.get("cost");
                    act.setCost(costObj != null ? BigDecimal.valueOf(Double.parseDouble(costObj.toString())) : BigDecimal.ZERO);

                    activities.add(act);
                }
            }
            savedPlan.getActivities().addAll(activities);

            // 6. Populate highlights
            List<String> interestsList = java.util.Arrays.asList("景点体验", "特色美食", "文化探索");
            java.util.Set<PlanHighlight> highlights = new java.util.HashSet<>();
            for (String text : interestsList) {
                PlanHighlight h = new PlanHighlight();
                h.setHighlightText(text);
                h.setPlanId(savedPlan.getId());
                highlights.add(h);
            }
            savedPlan.setHighlights(highlights);

            TravelPlan finalPlan = travelPlanRepository.save(savedPlan);
            logger.info("Successfully parsed and saved plan {} from conversation {}", finalPlan.getId(), conversationId);
            return convertToResponse(finalPlan);
        } catch (RuntimeException e) {
            logger.error("parseAndSaveConversation failed for conversation {}: {}", conversationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in parseAndSaveConversation for conversation {}", conversationId, e);
            throw new RuntimeException("解析行程时发生未知错误: " + e.getMessage(), e);
        }
    }
}