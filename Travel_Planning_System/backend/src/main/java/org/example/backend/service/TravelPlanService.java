package org.example.backend.service;

import org.example.backend.dto.TravelPlanRequest;
import org.example.backend.dto.TravelPlanResponse;
import org.example.backend.entity.PlanHighlight;
import org.example.backend.entity.TravelPlan;
import org.example.backend.repository.TravelPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TravelPlanService {

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    /**
     * Generate and save a travel plan using AgentLLM
     * This is a mock implementation - integrate with real AgentLLM API later
     */
    public TravelPlanResponse generateTravelPlan(TravelPlanRequest request, Long userId) {
        // Generate itinerary
        String itinerary = generateItinerary(request);
        
        // Create TravelPlan entity
        TravelPlan plan = new TravelPlan();
        plan.setUserId(userId);
        plan.setTitle("完美的" + request.getDestination() + "之旅");
        plan.setDestinationName(request.getDestination());
        plan.setDays(request.getDays());
        plan.setItinerary(itinerary);
        plan.setEstimatedBudget(BigDecimal.valueOf(request.getBudget()));
        plan.setAiConfidenceScore(BigDecimal.valueOf(0.92));
        plan.setInterests(request.getInterests());
        plan.setTravelStyle(request.getTravelStyle());
        plan.setStatus("draft");
        
        // First save the plan to get its ID
        TravelPlan savedPlan = travelPlanRepository.save(plan);
        
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
        
        // Save highlights
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
                        "Day 1-3: 巴黎\nDay 4-7: 瑞士\nDay 8-10: 意大利",
                        24999,
                        0.95,
                        Arrays.asList("埃菲尔铁塔", "卢浮宫", "阿尔卑斯山", "威尼斯")
                ),
                new TravelPlanResponse(
                        2L,
                        "亚洲美食之旅",
                        "泰国",
                        7,
                        "Day 1-4: 曼谷\nDay 5-7: 清迈",
                        8999,
                        0.89,
                        Arrays.asList("大皇宫", "浮市场", "清迈古城")
                )
        );
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
                "🏨 精选住宿推荐"
        );
    }

    /**
     * Convert TravelPlan entity to DTO
     */
    private TravelPlanResponse convertToResponse(TravelPlan plan) {
        List<String> highlightTexts = plan.getHighlights() != null ?
                plan.getHighlights().stream()
                        .map(PlanHighlight::getHighlightText)
                        .collect(Collectors.toList()) :
                Arrays.asList("景点体验", "美食品尝", "文化探索");

        return new TravelPlanResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getDestinationName(),
                plan.getDays(),
                plan.getItinerary(),
                plan.getEstimatedBudget() != null ? plan.getEstimatedBudget().intValue() : 0,
                plan.getAiConfidenceScore() != null ? plan.getAiConfidenceScore().doubleValue() : 0.0,
                highlightTexts
        );
    }
}
