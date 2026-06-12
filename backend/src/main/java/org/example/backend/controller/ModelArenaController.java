package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.arena.ArenaAutoResponse;
import org.example.backend.dto.arena.ArenaLeaderboardResponse;
import org.example.backend.dto.arena.ArenaVoteRequest;
import org.example.backend.service.ModelArenaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/arena")
@CrossOrigin(origins = "*")
public class ModelArenaController {

    private final ModelArenaService modelArenaService;

    public ModelArenaController(ModelArenaService modelArenaService) {
        this.modelArenaService = modelArenaService;
    }

    @GetMapping("/models")
    public ApiResponse<List<String>> listModels() {
        return ApiResponse.success(modelArenaService.listModels());
    }

    @PostMapping(value = "/auto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ArenaAutoResponse> autoCompare(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "chatHistoryJson", required = false) String chatHistoryJson,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        if (query == null || query.trim().isEmpty()) {
            return ApiResponse.error("query required");
        }
        ArenaAutoResponse response = modelArenaService.runAutoComparison(
                query.trim(), userId, chatHistoryJson, file
        );
        return ApiResponse.success(response);
    }

    @PostMapping(value = "/auto/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter autoCompareStream(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "chatHistoryJson", required = false) String chatHistoryJson,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return modelArenaService.streamAutoComparison(query.trim(), userId, chatHistoryJson, file);
    }

    @PostMapping("/vote")
    public ApiResponse<Void> submitVote(@RequestBody ArenaVoteRequest request) {
        modelArenaService.recordVote(request);
        return ApiResponse.success(null, "ok");
    }

    @GetMapping("/leaderboard")
    public ApiResponse<ArenaLeaderboardResponse> leaderboard() {
        return ApiResponse.success(modelArenaService.getLeaderboard());
    }

    @GetMapping("/pairwise")
    public ApiResponse<org.example.backend.dto.arena.PairwiseMatrixResponse> pairwise() {
        return ApiResponse.success(modelArenaService.getPairwiseMatrix());
    }
}
