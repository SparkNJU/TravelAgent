package com.tripagent.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Puts merged .env into the Spring {@link org.springframework.core.env.Environment} early.
 * JVM system properties are already set in {@link com.tripagent.backend.BackendApplication#main}.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

  private static final String SOURCE_NAME = "tripagentDotenv";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, String> merged = TripAgentDotenvLoader.loadMergedEnv();
    if (merged.isEmpty()) {
      return;
    }
    Map<String, Object> props = new LinkedHashMap<>(merged);
    environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    log.info("Spring Environment: added {} entries from .env (source={})", props.size(), SOURCE_NAME);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
