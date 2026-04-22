package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.DestinationResponse;
import org.example.backend.dto.TravelPlanRequest;
import org.example.backend.dto.TravelPlanResponse;
import org.example.backend.service.DestinationService;
import org.example.backend.service.TravelPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
