package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.repository.ModelProfileRepository;
import com.tripagent.backend.service.llm.LlmChatRequest;
import com.tripagent.backend.service.llm.LlmChatResponse;
import com.tripagent.backend.service.llm.LlmGateway;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 仅用于运维/排障，验证 LLM 连通性。生产环境也可保留作健康检查。 */
@RestController
@RequestMapping("/api/eval/llm")
public class LlmHealthController {

  private final LlmGateway gateway;
  private final ModelProfileRepository repo;

  public LlmHealthController(LlmGateway gateway, ModelProfileRepository repo) {
    this.gateway = gateway;
    this.repo = repo;
  }

  @GetMapping("/ping")
  public ResponseEntity<EvalApiResponse<Map<String, Object>>> ping(
      @RequestParam(required = false) Long modelProfileId,
      @RequestParam(required = false) String modelId,
      @RequestParam(required = false, defaultValue = "用一句话简单介绍杭州西湖") String prompt
  ) {
    ModelProfile profile;
    if (modelProfileId != null) {
      profile = repo.findById(modelProfileId)
          .orElseThrow(() -> new IllegalArgumentException("modelProfileId 不存在: " + modelProfileId));
    } else if (modelId != null && !modelId.isBlank()) {
      profile = repo.findByModelId(modelId.trim())
          .orElseThrow(() -> new IllegalArgumentException("modelId 不存在: " + modelId));
    } else {
      throw new IllegalArgumentException("必须提供 modelProfileId 或 modelId 之一");
    }

    LlmChatResponse resp = gateway.invokeProfile(
        profile,
        List.of(LlmChatRequest.Message.user(prompt))
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("modelProfileId", profile.getModelProfileId());
    result.put("modelId", profile.getModelId());
    result.put("text", resp.text());
    result.put("promptTokens", resp.promptTokens());
    result.put("completionTokens", resp.completionTokens());
    result.put("totalTokens", resp.totalTokens());
    result.put("latencyMs", resp.latencyMs());
    result.put("finishReason", resp.finishReason());
    return ResponseEntity.ok(EvalApiResponse.success(result));
  }
}
