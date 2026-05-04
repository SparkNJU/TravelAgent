package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.service.TripAssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
@CrossOrigin(origins = "*")
public class TripAssistantController {

    @Autowired
    private TripAssistantService tripAssistantService;

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> chat(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        if (query == null || query.trim().isEmpty()) {
            return ApiResponse.error("query required");
        }
        Map<String, Object> result = tripAssistantService.generateTripPlan(query.trim(), file, userId);
        return ApiResponse.success(result);
    }
}