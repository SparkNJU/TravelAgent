package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.DestinationResponse;
import org.example.backend.dto.SavePlanRequest;
import org.example.backend.dto.TravelPlanRequest;
import org.example.backend.dto.TravelPlanResponse;
import org.example.backend.service.DestinationService;
import org.example.backend.service.TravelPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/travel")
@CrossOrigin(origins = "*")
public class TravelController {

    @Autowired
    private DestinationService destinationService;

    @Autowired
    private TravelPlanService travelPlanService;

    /**
     * Get all popular destinations
     */
    @GetMapping("/destinations/popular")
    public ApiResponse<List<DestinationResponse>> getPopularDestinations() {
        List<DestinationResponse> destinations = destinationService.getPopularDestinations();
        return ApiResponse.success(destinations);
    }

    /**
     * Search destinations by keyword
     */
    @GetMapping("/destinations/search")
    public ApiResponse<List<DestinationResponse>> searchDestinations(@RequestParam String keyword) {
        List<DestinationResponse> results = destinationService.searchDestinations(keyword);
        return ApiResponse.success(results);
    }

    /**
     * Generate AI-powered travel plan
     */
    @PostMapping("/plan/generate")
    public ApiResponse<TravelPlanResponse> generateTravelPlan(@RequestBody TravelPlanRequest request) {
        if (request.getDestination() == null || request.getDays() == null || request.getUserId() == null) {
            return ApiResponse.error("Missing required parameters: destination, days, and userId");
        }

        TravelPlanResponse plan = travelPlanService.generateTravelPlan(request, request.getUserId());
        return ApiResponse.success(plan);
    }

    private List<org.example.backend.entity.PlanActivity> createMockActivities(Long planId, String dest, int days) {
        List<org.example.backend.entity.PlanActivity> list = new java.util.ArrayList<>();
        for (int i = 1; i <= days; i++) {
            org.example.backend.entity.PlanActivity act = new org.example.backend.entity.PlanActivity();
            act.setId((long) (planId * 100 + i));
            act.setPlanId(planId);
            act.setDayNumber(i);
            act.setActivityTime("09:00");
            act.setLocationName(dest + "游览点 " + i);
            act.setLatitude(java.math.BigDecimal.valueOf(30.0 + i * 0.1));
            act.setLongitude(java.math.BigDecimal.valueOf(110.0 + i * 0.1));
            act.setDescription(dest + "第" + i + "天的精选行程描述。");
            act.setTips("推荐游览时间：3小时。");
            act.setCost(java.math.BigDecimal.valueOf(50.0));
            list.add(act);
        }
        return list;
    }

    /**
     * Get sample travel plans for recommendations
     */
    @GetMapping("/plans/samples")
    public ApiResponse<List<TravelPlanResponse>> getSamplePlans() {
        List<TravelPlanResponse> samples = List.of(
                new TravelPlanResponse(
                        1L,
                        "梦幻欧洲10日游",
                        "法国",
                        10,
                        createMockActivities(1L, "法国", 10),
                        24999,
                        0.95,
                        List.of("埃菲尔铁塔", "卢浮宫", "阿尔卑斯山", "威尼斯")),
                new TravelPlanResponse(
                        2L,
                        "亚洲美食之旅",
                        "泰国",
                        7,
                        createMockActivities(2L, "泰国", 7),
                        8999,
                        0.89,
                        List.of("大皇宫", "水上市场", "清迈古城")));
        return ApiResponse.success(samples);
    }

    /**
     * Save a travel plan to user's personal plans
     */
    @PostMapping("/plan/save")
    public ApiResponse<TravelPlanResponse> saveTravelPlan(@RequestBody SavePlanRequest request) {
        if (request.getUserId() == null) {
            return ApiResponse.error("用户ID不能为空");
        }
        if (request.getTitle() == null || request.getTitle().isEmpty()) {
            return ApiResponse.error("标题不能为空");
        }
        if (request.getDestination() == null || request.getDestination().isEmpty()) {
            return ApiResponse.error("目的地不能为空");
        }

        TravelPlanResponse plan = travelPlanService.savePlan(request);
        return ApiResponse.success(plan);
    }

    /**
     * Get user's saved plans
     */
    @GetMapping("/plans/user/{userId}")
    public ApiResponse<List<TravelPlanResponse>> getUserPlans(@PathVariable Long userId) {
        List<TravelPlanResponse> plans = travelPlanService.getUserPlans(userId);
        return ApiResponse.success(plans);
    }

    /**
     * Get a specific plan by ID
     */
    @GetMapping("/plan/{planId}")
    public ApiResponse<TravelPlanResponse> getPlanById(@PathVariable Long planId) {
        TravelPlanResponse plan = travelPlanService.getPlanById(planId);
        if (plan != null) {
            return ApiResponse.success(plan);
        } else {
            return ApiResponse.error("规划不存在");
        }
    }

    @PutMapping("/plan/{planId}")
    public ApiResponse<TravelPlanResponse> updateTravelPlan(
            @PathVariable Long planId,
            @RequestBody SavePlanRequest request) {
        return ApiResponse.success(travelPlanService.updatePlan(planId, request));
    }

    /**
     * Delete a user's plan
     */
    @DeleteMapping("/plan/{planId}")
    public ApiResponse<Boolean> deletePlan(@PathVariable Long planId, @RequestParam Long userId) {
        boolean deleted = travelPlanService.deletePlan(planId, userId);
        if (deleted) {
            return ApiResponse.success(true);
        } else {
            return ApiResponse.error("删除失败，计划不存在或无权限");
        }
    }

    /**
     * Parse conversation history into a structured plan and save
     */
    @PostMapping("/plan/parse-and-save")
    public ApiResponse<TravelPlanResponse> parseAndSave(@RequestBody Map<String, Object> request) {
        if (!request.containsKey("conversationId")) {
            return ApiResponse.error("Missing conversationId");
        }
        try {
            Long conversationId = Long.valueOf(request.get("conversationId").toString());
            TravelPlanResponse savedPlan = travelPlanService.parseAndSaveConversation(conversationId);
            return ApiResponse.success(savedPlan);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}