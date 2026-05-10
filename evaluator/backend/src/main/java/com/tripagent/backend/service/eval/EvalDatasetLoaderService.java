package com.tripagent.backend.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.entity.Dataset;
import com.tripagent.backend.entity.DatasetSample;
import com.tripagent.backend.repository.DatasetRepository;
import com.tripagent.backend.repository.DatasetSampleRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 数据集加载入口。loadSamples 优先查 DB（dataset 表），不存在再回退 classpath 文件。
 * 解析逻辑（JSON/CSV）以 public 方法暴露，给 DatasetService 上传与 DatasetSeedService 启动 seed 复用。
 */
@Service
public class EvalDatasetLoaderService {

  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final DatasetRepository datasetRepository;
  private final DatasetSampleRepository datasetSampleRepository;

  public EvalDatasetLoaderService(
      ResourceLoader resourceLoader,
      ObjectMapper objectMapper,
      DatasetRepository datasetRepository,
      DatasetSampleRepository datasetSampleRepository
  ) {
    this.resourceLoader = resourceLoader;
    this.objectMapper = objectMapper;
    this.datasetRepository = datasetRepository;
    this.datasetSampleRepository = datasetSampleRepository;
  }

  public List<EvalDatasetSample> loadSamples(String datasetId) {
    String normalized = datasetId == null ? "" : datasetId.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("datasetId 不能为空");
    }

    // DB 优先：按 name 查 dataset 表
    Optional<Dataset> dbDataset = datasetRepository.findByName(normalized);
    if (dbDataset.isPresent() && Boolean.TRUE.equals(dbDataset.get().getEnabled())) {
      return loadFromDb(dbDataset.get());
    }

    // 回退到 classpath 文件
    Resource jsonResource = resourceLoader.getResource("classpath:datasets/" + normalized + ".json");
    if (jsonResource.exists()) {
      try (InputStream in = jsonResource.getInputStream()) {
        return parseJsonStream(in, normalized);
      } catch (IOException ex) {
        throw new IllegalArgumentException("解析 JSON 数据集失败: " + normalized + ", " + ex.getMessage(), ex);
      }
    }

    Resource csvResource = resourceLoader.getResource("classpath:datasets/" + normalized + ".csv");
    if (csvResource.exists()) {
      try (InputStream in = csvResource.getInputStream()) {
        return parseCsvStream(in, normalized);
      } catch (IOException ex) {
        throw new IllegalArgumentException("解析 CSV 数据集失败: " + normalized + ", " + ex.getMessage(), ex);
      }
    }

    throw new IllegalArgumentException("未找到数据集: " + normalized + "（DB 与 classpath 均无）");
  }

  /** 从 DB 中读取 dataset_sample 转成 EvalDatasetSample。 */
  private List<EvalDatasetSample> loadFromDb(Dataset dataset) {
    List<DatasetSample> rows = datasetSampleRepository
        .findByDatasetDatasetIdOrderBySortOrderAsc(dataset.getDatasetId());
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("数据集 " + dataset.getName() + " 在 DB 中无样本");
    }
    List<EvalDatasetSample> result = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      DatasetSample r = rows.get(i);
      String key = r.getSampleKey() != null && !r.getSampleKey().isBlank()
          ? r.getSampleKey() : (dataset.getName() + "-" + (i + 1));
      result.add(new EvalDatasetSample(key, r.getInput(), r.getExpectedOutput()));
    }
    return result;
  }

  /** JSON 流解析：数组形式 [{id, input, expectedOutput}, ...]。供上传 / seed 共用。 */
  public List<EvalDatasetSample> parseJsonStream(InputStream input, String datasetIdHint) throws IOException {
    List<Map<String, Object>> rows = objectMapper.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
    return normalizeRows(rows, datasetIdHint);
  }

  /** CSV 流解析：第一行表头，必须含 input 列。供上传 / seed 共用。 */
  public List<EvalDatasetSample> parseCsvStream(InputStream input, String datasetIdHint) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        throw new IllegalArgumentException("CSV 数据集为空: " + datasetIdHint);
      }

      String[] headers = splitCsvLine(headerLine);
      Map<String, Integer> indexMap = new LinkedHashMap<>();
      for (int i = 0; i < headers.length; i++) {
        indexMap.put(headers[i].trim(), i);
      }

      List<Map<String, Object>> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;
        String[] parts = splitCsvLine(line);
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : indexMap.entrySet()) {
          int idx = entry.getValue();
          row.put(entry.getKey(), idx < parts.length ? parts[idx].trim() : "");
        }
        rows.add(row);
      }

      return normalizeRows(rows, datasetIdHint);
    }
  }

  private List<EvalDatasetSample> normalizeRows(List<Map<String, Object>> rows, String datasetIdHint) {
    List<EvalDatasetSample> samples = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> row = rows.get(i);
      String sampleId = stringValue(row, "id", datasetIdHint + "-" + (i + 1));
      String input = stringValue(row, "input", "");
      String expectedOutput = stringValue(row, "expectedOutput", null);
      if (expectedOutput == null) expectedOutput = stringValue(row, "expected", "");
      if (expectedOutput.isBlank()) expectedOutput = stringValue(row, "expected_output", "");

      if (input.isBlank()) continue;
      samples.add(new EvalDatasetSample(sampleId, input, expectedOutput));
    }

    if (samples.isEmpty()) {
      throw new IllegalArgumentException("数据集未包含有效样本: " + datasetIdHint);
    }
    return samples;
  }

  private String stringValue(Map<String, Object> row, String key, String defaultValue) {
    Object value = row.get(key);
    if (value == null) return defaultValue;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? defaultValue : text;
  }

  private String[] splitCsvLine(String line) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        quoted = !quoted;
        continue;
      }
      if (c == ',' && !quoted) {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    result.add(current.toString());
    return result.toArray(new String[0]);
  }
}
