package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/map")
@CrossOrigin(origins = "*")
public class MapController {

    private static final String GAODE_API_KEY = "f82d8169742adbacb555271943b16e2b";
    private static final String GAODE_PLACE_SEARCH_URL = "https://restapi.amap.com/v3/place/text";
    private static final String GAODE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";

    private final RestTemplate restTemplate;

    public MapController() {
        this.restTemplate = new RestTemplate();
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> searchPlace(@RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "全国") String city) {
        try {
            String url = GAODE_PLACE_SEARCH_URL + "?key=" + GAODE_API_KEY +
                    "&keywords=" + keyword + "&city=" + city + "&output=json&pageSize=10";
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            return ApiResponse.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("搜索失败: " + e.getMessage());
        }
    }

    @GetMapping("/geocode")
    public ApiResponse<Map<String, Object>> geocode(@RequestParam String address) {
        try {
            String url = GAODE_GEOCODE_URL + "?key=" + GAODE_API_KEY + "&address=" + address + "&output=json";
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            return ApiResponse.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("地理编码失败: " + e.getMessage());
        }
    }
}