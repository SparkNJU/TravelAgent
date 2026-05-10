package com.tripagent.backend.service.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.config.LlmProperties;
import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.enums.ModelRole;
import com.tripagent.backend.repository.ModelProfileRepository;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class LlmGateway {

  private final OpenAiCompatibleClient client;
  private final ModelProfileRepository modelProfileRepository;
  private final LlmProperties props;
  private final ObjectMapper objectMapper;
  private final Environment environment;

  public LlmGateway(
      OpenAiCompatibleClient client,
      ModelProfileRepository modelProfileRepository,
      LlmProperties props,
      ObjectMapper objectMapper,
      Environment environment
  ) {
    this.client = client;
    this.modelProfileRepository = modelProfileRepository;
    this.props = props;
    this.objectMapper = objectMapper;
    this.environment = environment;
  }

  /** 通过 ModelProfile 主键调 LLM。 */
  public LlmChatResponse invoke(Long modelProfileId, List<LlmChatRequest.Message> messages) {
    ModelProfile profile = modelProfileRepository.findById(modelProfileId)
        .orElseThrow(() -> new IllegalArgumentException("模型不存在: id=" + modelProfileId));
    return invokeProfile(profile, messages);
  }

  /** 直接通过 ModelProfile 对象调 LLM (避免重复查 DB)。 */
  public LlmChatResponse invokeProfile(ModelProfile profile, List<LlmChatRequest.Message> messages) {
    if (!Boolean.TRUE.equals(profile.getEnabled())) {
      throw new IllegalStateException("模型已禁用: " + profile.getModelId());
    }

    String apiKey = resolveApiKey(profile);
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "API key 未配置: profile=" + profile.getModelId() + ", apiKeyRef=" + profile.getApiKeyRef()
              + "。请设置 OS 环境变量或 JVM 属性 MODELSCOPE_API_KEY，或在 .env 中配置 MODELSCOPE_API_KEY / "
              + "MODELSCOPE_ACCESS_TOKEN（官方文档名）/ API_KEY；并确认后端工作目录能解析到 tripAgent/.env，"
              + "或设置 TRIPAGENT_DOTENV_PATH 指向 .env。");
    }

    Double temperature = null;
    Integer maxTokens = null;
    String paramsJson = profile.getDefaultParams();
    if (paramsJson != null && !paramsJson.isBlank()) {
      try {
        Map<String, Object> parsed = objectMapper.readValue(
            paramsJson, new TypeReference<Map<String, Object>>() {});
        Object t = parsed.get("temperature");
        if (t instanceof Number n) {
          temperature = n.doubleValue();
        }
        Object mt = parsed.get("maxTokens");
        if (mt == null) {
          mt = parsed.get("max_tokens");
        }
        if (mt instanceof Number n) {
          maxTokens = n.intValue();
        }
      } catch (Exception ignored) {
      }
    }

    LlmChatRequest req = new LlmChatRequest(
        profile.getModelId(),
        messages,
        temperature,
        maxTokens,
        apiKey,
        profile.getApiBaseUrl()
    );
    return client.chat(req);
  }

  /**
   * 解析 apiKey：先用 profile.apiKeyRef 指定的环境变量，找不到/未配置就回退到全局
   * llm.openai-compatible.api-key（judge 角色优先用 judge-api-key）。
   */
  private String resolveApiKey(ModelProfile profile) {
    String ref = profile.getApiKeyRef();
    if (ref != null && !ref.isBlank()) {
      String resolved = resolveExternalProperty(ref);
      if (resolved != null && !resolved.isBlank()) {
        return resolved;
      }
      // 文档常用 MODELSCOPE_ACCESS_TOKEN；profile 仍写 apiKeyRef=MODELSCOPE_API_KEY
      if ("MODELSCOPE_API_KEY".equals(ref)) {
        resolved = firstNonBlank(
            resolveExternalProperty("MODELSCOPE_ACCESS_TOKEN"),
            resolveExternalProperty("API_KEY"),
            resolveExternalProperty("MS_API_KEY"));
        if (resolved != null && !resolved.isBlank()) {
          return resolved;
        }
      }
    }
    if (profile.getRole() == ModelRole.JUDGE) {
      String judge = props.getJudgeApiKey();
      if (judge != null && !judge.isBlank()) {
        return judge;
      }
    }
    return props.getApiKey();
  }

  /** OS env, then JVM system properties, then Spring Environment (includes merged .env). */
  private String resolveExternalProperty(String name) {
    String v = System.getenv(name);
    if (v != null && !v.isBlank()) {
      return v;
    }
    v = System.getProperty(name);
    if (v != null && !v.isBlank()) {
      return v;
    }
    return environment.getProperty(name);
  }

  private static String firstNonBlank(String a, String b, String c) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    if (c != null && !c.isBlank()) {
      return c;
    }
    return null;
  }
}
