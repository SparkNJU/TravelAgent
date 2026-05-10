package com.tripagent.backend.service.eval;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 启动时把 classpath:datasets/*.json|*.csv 中的内置数据集 seed 到 DB（按 name 幂等）。
 * 用户软删后重启不会回灌（DatasetService.seedBuiltin 用 findByName 已存在则跳过）。
 */
@Component
public class DatasetSeedService {

  private final DatasetService datasetService;
  private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

  public DatasetSeedService(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @PostConstruct
  public void seed() {
    Resource[] resources;
    try {
      resources = resolver.getResources("classpath:datasets/*");
    } catch (IOException ex) {
      return;
    }

    for (Resource resource : resources) {
      String filename = resource.getFilename();
      if (filename == null) continue;
      String lower = filename.toLowerCase(Locale.ROOT);
      boolean isJson = lower.endsWith(".json");
      boolean isCsv = lower.endsWith(".csv");
      if (!isJson && !isCsv) continue;

      String name = filename.substring(0, filename.lastIndexOf('.'));
      try (InputStream in = resource.getInputStream()) {
        byte[] bytes = in.readAllBytes();
        datasetService.seedBuiltin(
            name,
            name,
            "内置数据集（自动从 classpath 同步）",
            bytes,
            isJson
        );
      } catch (Exception ignored) {
        // 单个数据集失败不阻塞启动
      }
    }
  }
}
