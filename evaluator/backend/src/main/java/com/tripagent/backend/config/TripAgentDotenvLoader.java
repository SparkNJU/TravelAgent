package com.tripagent.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared .env discovery + ModelScope key aliases. Used from {@code main()} (before Spring) and
 * {@link DotenvEnvironmentPostProcessor} so {@code MODELSCOPE_API_KEY} is always visible via
 * {@link System#getProperty} / {@link org.springframework.core.env.Environment}.
 *
 * <p>Official ModelScope docs often show {@code MODELSCOPE_ACCESS_TOKEN} in Python samples; this
 * project’s model profiles use {@code apiKeyRef=MODELSCOPE_API_KEY} — we map the former to the
 * latter when needed.</p>
 */
public final class TripAgentDotenvLoader {

  private static final Logger log = LoggerFactory.getLogger(TripAgentDotenvLoader.class);

  private TripAgentDotenvLoader() {
  }

  /**
   * Must run before {@link org.springframework.boot.SpringApplication#run} so placeholder
   * resolution and {@code System#getProperty} see ModelScope keys.
   */
  public static void installIntoSystemProperties() {
    Map<String, String> merged = loadMergedEnv();
    if (merged.isEmpty()) {
      log.warn(
          "未加载到任何 .env（已按 user.dir 搜索常见路径）。请设置 OS 环境变量 MODELSCOPE_API_KEY，"
              + "或设置 TRIPAGENT_DOTENV_PATH 指向 .env 文件，或将 tripAgent/.env 放在工作目录附近。");
      return;
    }
    int n = 0;
    for (Map.Entry<String, String> e : merged.entrySet()) {
      String k = e.getKey();
      String v = e.getValue();
      if (k == null || k.isBlank() || v == null || v.isBlank()) {
        continue;
      }
      if (!isBlank(System.getenv(k))) {
        continue;
      }
      if (!isBlank(System.getProperty(k))) {
        continue;
      }
      System.setProperty(k, v);
      n++;
    }
    if (log.isDebugEnabled()) {
      log.debug("Installed {} JVM system properties from .env (values not logged)", n);
    }
  }

  /** Merge candidate .env files; later files override keys from earlier files. */
  public static Map<String, String> loadMergedEnv() {
    Map<String, String> merged = new LinkedHashMap<>();
    Set<Path> seen = new LinkedHashSet<>();
    for (Path candidate : candidateEnvFiles()) {
      try {
        Path abs = candidate.toAbsolutePath().normalize();
        if (!seen.add(abs) || !Files.isRegularFile(abs)) {
          continue;
        }
        mergeFile(abs, merged);
      } catch (Exception ex) {
        log.debug("Skip .env candidate {}: {}", candidate, ex.getMessage());
      }
    }
    applyModelScopeAliases(merged);
    return merged;
  }

  private static void applyModelScopeAliases(Map<String, String> merged) {
    if (!isBlank(merged.get("MODELSCOPE_API_KEY"))) {
      return;
    }
    String v = firstNonBlank(
        merged.get("MODELSCOPE_ACCESS_TOKEN"),
        merged.get("MS_API_KEY"),
        merged.get("API_KEY"));
    if (!isBlank(v)) {
      merged.put("MODELSCOPE_API_KEY", v);
    }
  }

  private static void mergeFile(Path envFile, Map<String, String> into) {
    Dotenv dotenv = Dotenv.configure()
        .directory(envFile.getParent().toString())
        .filename(envFile.getFileName().toString())
        .ignoreIfMalformed()
        .ignoreIfMissing()
        .load();
    dotenv.entries().forEach(e -> {
      String k = e.getKey();
      if (k != null && !k.isBlank()) {
        into.put(k, e.getValue() == null ? "" : e.getValue());
      }
    });
    log.debug("Merged .env from {}", envFile.toAbsolutePath().normalize());
  }

  static Iterable<Path> candidateEnvFiles() {
    String override = firstNonBlank(System.getenv("TRIPAGENT_DOTENV_PATH"), System.getProperty("tripagent.dotenv.path"));
    if (!isBlank(override)) {
      return java.util.List.of(Paths.get(override.trim()));
    }

    String userDir = System.getProperty("user.dir", ".");
    Path ud = Paths.get(userDir);
    return java.util.List.of(
        ud.resolve(".env").normalize(),
        ud.resolve("..").resolve(".env").normalize(),
        ud.resolve("..").resolve("..").resolve(".env").normalize(),
        ud.resolve("tripAgent").resolve(".env").normalize(),
        ud.resolve("..").resolve("tripAgent").resolve(".env").normalize(),
        ud.resolve("..").resolve("..").resolve("tripAgent").resolve(".env").normalize()
    );
  }

  private static String firstNonBlank(String... xs) {
    if (xs == null) {
      return null;
    }
    for (String x : xs) {
      if (!isBlank(x)) {
        return x;
      }
    }
    return null;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
