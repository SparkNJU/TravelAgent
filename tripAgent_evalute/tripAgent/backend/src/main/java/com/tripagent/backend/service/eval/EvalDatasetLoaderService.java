package com.tripagent.backend.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class EvalDatasetLoaderService {

  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;

  public EvalDatasetLoaderService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
    this.resourceLoader = resourceLoader;
    this.objectMapper = objectMapper;
  }

  public List<EvalDatasetSample> loadSamples(String datasetId) {
    String normalized = datasetId == null ? "" : datasetId.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("datasetId 不能为空");
    }

    Resource jsonResource = resourceLoader.getResource("classpath:datasets/" + normalized + ".json");
    if (jsonResource.exists()) {
      return loadJson(jsonResource, normalized);
    }

    Resource csvResource = resourceLoader.getResource("classpath:datasets/" + normalized + ".csv");
    if (csvResource.exists()) {
      return loadCsv(csvResource, normalized);
    }

    throw new IllegalArgumentException("未找到数据集文件: " + normalized + "（支持 .json 或 .csv）");
  }

  private List<EvalDatasetSample> loadJson(Resource resource, String datasetId) {
    try (InputStream inputStream = resource.getInputStream()) {
      List<Map<String, Object>> rows = objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {
      });
      return normalizeRows(rows, datasetId);
    } catch (IOException ex) {
      throw new IllegalArgumentException("解析 JSON 数据集失败: " + datasetId + ", " + ex.getMessage(), ex);
    }
  }

  private List<EvalDatasetSample> loadCsv(Resource resource, String datasetId) {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        throw new IllegalArgumentException("CSV 数据集为空: " + datasetId);
      }

      String[] headers = splitCsvLine(headerLine);
      Map<String, Integer> indexMap = new LinkedHashMap<>();
      for (int i = 0; i < headers.length; i++) {
        indexMap.put(headers[i].trim(), i);
      }

      List<Map<String, Object>> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        String[] parts = splitCsvLine(line);
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : indexMap.entrySet()) {
          int idx = entry.getValue();
          row.put(entry.getKey(), idx < parts.length ? parts[idx].trim() : "");
        }
        rows.add(row);
      }

      return normalizeRows(rows, datasetId);
    } catch (IOException ex) {
      throw new IllegalArgumentException("解析 CSV 数据集失败: " + datasetId + ", " + ex.getMessage(), ex);
    }
  }

  private List<EvalDatasetSample> normalizeRows(List<Map<String, Object>> rows, String datasetId) {
    List<EvalDatasetSample> samples = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> row = rows.get(i);
      String sampleId = stringValue(row, "id", datasetId + "-" + (i + 1));
      String input = stringValue(row, "input", "");
      String expectedOutput = stringValue(row, "expectedOutput", null);
      if (expectedOutput == null) {
        expectedOutput = stringValue(row, "expected", "");
      }
      if (expectedOutput.isBlank()) {
        expectedOutput = stringValue(row, "expected_output", "");
      }

      if (input.isBlank()) {
        continue;
      }
      samples.add(new EvalDatasetSample(sampleId, input, expectedOutput));
    }

    if (samples.isEmpty()) {
      throw new IllegalArgumentException("数据集未包含有效样本: " + datasetId);
    }
    return samples;
  }

  private String stringValue(Map<String, Object> row, String key, String defaultValue) {
    Object value = row.get(key);
    if (value == null) {
      return defaultValue;
    }
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
