package com.tripagent.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "llm.openai-compatible")
public class LlmProperties {

  private String baseUrl = "https://api-inference.modelscope.cn/v1";
  private String apiKey = "";
  private String judgeApiKey = "";
  private Integer timeoutSeconds = 60;
  private Integer maxRetries = 2;
}
