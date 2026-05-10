package com.tripagent.backend.service.eval;

import com.tripagent.backend.dto.eval.DatasetResponse;
import com.tripagent.backend.dto.eval.DatasetSampleResponse;
import com.tripagent.backend.entity.Dataset;
import com.tripagent.backend.entity.DatasetSample;
import com.tripagent.backend.entity.enums.DatasetSource;
import com.tripagent.backend.repository.DatasetRepository;
import com.tripagent.backend.repository.DatasetSampleRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DatasetService {

  private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,80}$");
  private static final long MAX_FILE_BYTES = 1024L * 1024L; // 1MB
  private static final int MAX_SAMPLES = 200;

  private final DatasetRepository datasetRepository;
  private final DatasetSampleRepository datasetSampleRepository;
  private final EvalDatasetLoaderService loaderService;

  public DatasetService(
      DatasetRepository datasetRepository,
      DatasetSampleRepository datasetSampleRepository,
      EvalDatasetLoaderService loaderService
  ) {
    this.datasetRepository = datasetRepository;
    this.datasetSampleRepository = datasetSampleRepository;
    this.loaderService = loaderService;
  }

  @Transactional(readOnly = true)
  public List<DatasetResponse> list(DatasetSource source, Boolean enabledOnly) {
    List<Dataset> datasets;
    if (Boolean.TRUE.equals(enabledOnly)) {
      datasets = datasetRepository.findByEnabledTrueOrderByDatasetIdAsc();
    } else if (source != null) {
      datasets = datasetRepository.findBySourceOrderByDatasetIdAsc(source);
    } else {
      datasets = datasetRepository.findAllByOrderByDatasetIdAsc();
    }
    if (Boolean.TRUE.equals(enabledOnly) && source != null) {
      datasets = datasets.stream().filter(d -> d.getSource() == source).toList();
    }
    return datasets.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public DatasetResponse get(Long id) {
    return toResponse(getOrThrow(id));
  }

  @Transactional(readOnly = true)
  public List<DatasetSampleResponse> getSamples(Long id, int limit) {
    Dataset dataset = getOrThrow(id);
    List<DatasetSample> samples = datasetSampleRepository
        .findByDatasetDatasetIdOrderBySortOrderAsc(dataset.getDatasetId());
    int safeLimit = Math.max(1, Math.min(limit, samples.size()));
    return samples.stream().limit(safeLimit).map(this::toSampleResponse).toList();
  }

  /** 用户上传新数据集（multipart）。 */
  @Transactional
  public DatasetResponse upload(MultipartFile file, String name, String displayName, String description, String owner) {
    validateNameOrThrow(name);
    if (datasetRepository.existsByName(name)) {
      throw new IllegalArgumentException("数据集名称已存在: " + name);
    }
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("上传文件为空");
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      throw new IllegalArgumentException("文件大小超过 1MB 上限: " + file.getSize() + " bytes");
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException ex) {
      throw new IllegalArgumentException("读取上传文件失败: " + ex.getMessage(), ex);
    }

    String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    boolean isJson = filename.endsWith(".json")
        || (file.getContentType() != null && file.getContentType().contains("json"));
    boolean isCsv = filename.endsWith(".csv")
        || (file.getContentType() != null && file.getContentType().contains("csv"));
    if (!isJson && !isCsv) {
      throw new IllegalArgumentException("仅支持 .json / .csv 文件");
    }

    List<EvalDatasetSample> parsed = parse(bytes, isJson, name);
    if (parsed.size() > MAX_SAMPLES) {
      throw new IllegalArgumentException("样本数超过上限 " + MAX_SAMPLES + ": " + parsed.size());
    }

    Dataset dataset = new Dataset();
    dataset.setName(name);
    dataset.setDisplayName(displayName == null || displayName.isBlank() ? name : displayName.trim());
    dataset.setSource(DatasetSource.USER);
    dataset.setOwner(owner);
    dataset.setSampleCount(parsed.size());
    dataset.setDescription(description);
    dataset.setEnabled(true);
    dataset.setSchemaHash(sha256(bytes));
    Dataset saved = datasetRepository.save(dataset);

    persistSamples(saved, parsed);
    return toResponse(saved);
  }

  /** Seed 服务调用，写入内置数据集到 DB。 */
  @Transactional
  public Dataset seedBuiltin(String name, String displayName, String description, byte[] content, boolean isJson) {
    Optional<Dataset> existing = datasetRepository.findByName(name);
    String hash = sha256(content);
    if (existing.isPresent()) {
      // 已存在则跳过；不强制更新（用户可能已软删）
      return existing.get();
    }

    List<EvalDatasetSample> parsed = parse(content, isJson, name);

    Dataset dataset = new Dataset();
    dataset.setName(name);
    dataset.setDisplayName(displayName == null || displayName.isBlank() ? name : displayName);
    dataset.setSource(DatasetSource.BUILTIN);
    dataset.setOwner("system");
    dataset.setSampleCount(parsed.size());
    dataset.setDescription(description);
    dataset.setEnabled(true);
    dataset.setSchemaHash(hash);
    Dataset saved = datasetRepository.save(dataset);
    persistSamples(saved, parsed);
    return saved;
  }

  @Transactional
  public void softDelete(Long id) {
    Dataset dataset = getOrThrow(id);
    dataset.setEnabled(false);
    datasetRepository.save(dataset);
  }

  private List<EvalDatasetSample> parse(byte[] bytes, boolean isJson, String hint) {
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      return isJson ? loaderService.parseJsonStream(in, hint) : loaderService.parseCsvStream(in, hint);
    } catch (IOException ex) {
      throw new IllegalArgumentException("解析数据集失败: " + ex.getMessage(), ex);
    }
  }

  private void persistSamples(Dataset dataset, List<EvalDatasetSample> samples) {
    for (int i = 0; i < samples.size(); i++) {
      EvalDatasetSample s = samples.get(i);
      DatasetSample row = new DatasetSample();
      row.setDataset(dataset);
      row.setSampleKey(s.sampleId());
      row.setInput(s.input());
      row.setExpectedOutput(s.expectedOutput());
      row.setSortOrder(i);
      datasetSampleRepository.save(row);
    }
  }

  private Dataset getOrThrow(Long id) {
    return datasetRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("数据集不存在: id=" + id));
  }

  private void validateNameOrThrow(String name) {
    if (name == null || !NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "name 必须符合正则 " + NAME_PATTERN.pattern() + "（字母数字下划线连字符，长度 3-80）: " + name);
    }
  }

  private String sha256(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      return "no-hash";
    }
  }

  private DatasetResponse toResponse(Dataset d) {
    return new DatasetResponse(
        d.getDatasetId(),
        d.getName(),
        d.getDisplayName(),
        d.getSource(),
        d.getOwner(),
        d.getSampleCount(),
        d.getDescription(),
        d.getEnabled(),
        d.getCreatedAt()
    );
  }

  private DatasetSampleResponse toSampleResponse(DatasetSample s) {
    return new DatasetSampleResponse(
        s.getSampleId(),
        s.getDataset().getDatasetId(),
        s.getSampleKey(),
        s.getInput(),
        s.getExpectedOutput(),
        s.getSortOrder()
    );
  }
}
