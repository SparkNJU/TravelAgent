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
                        "Day 1-3: 巴黎\nDay 4-7: 瑞士\nDay 8-10: 意大利",
                        24999,
                        0.95,
                        List.of("埃菲尔铁塔", "卢浮宫", "阿尔卑斯山", "威尼斯")),
                new TravelPlanResponse(
                        2L,
                        "亚洲美食之旅",
                        "泰国",
                        7,
                        "Day 1-4: 曼谷\nDay 5-7: 清迈",
                        8999,
                        0.89,
                        List.of("大皇宫", "浮市场", "清迈古城")));
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

    // TravelController.java 中需要添加更新接口
    @PutMapping("/plan/{planId}")
    public ApiResponse<TravelPlanResponse> updateTravelPlan(
            @PathVariable Long planId,
            @RequestBody Map<String, Object> request) {
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
}