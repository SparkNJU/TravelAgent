package com.tripagent.backend.service.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.dto.eval.ExportAuditPageResponse;
import com.tripagent.backend.dto.eval.ExportAuditResponse;
import com.tripagent.backend.dto.eval.ExportTaskBatchDeleteResponse;
import com.tripagent.backend.dto.eval.ExportConsistencyResponse;
import com.tripagent.backend.dto.eval.ExportMonitorMetricsResponse;
import com.tripagent.backend.dto.eval.ExportTaskPageResponse;
import com.tripagent.backend.dto.eval.ExportTaskResponse;
import com.tripagent.backend.dto.eval.RunCompareResponse;
import com.tripagent.backend.dto.eval.RunMetricDiffResponse;
import com.tripagent.backend.dto.eval.RunSampleDiffResponse;
import com.tripagent.backend.entity.EvalExportAudit;
import com.tripagent.backend.entity.EvalExportTask;
import com.tripagent.backend.entity.enums.ExportTaskStatus;
import com.tripagent.backend.repository.EvalExportAuditRepository;
import com.tripagent.backend.repository.EvalExportTaskRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalExportService {

  private static final String ACTION_CREATE = "CREATE";
  private static final String ACTION_START = "START";
  private static final String ACTION_SUCCESS = "SUCCESS";
  private static final String ACTION_FAILED = "FAILED";
  private static final String ACTION_RETRY = "RETRY";
  private static final String ACTION_DELETE = "DELETE";
  private static final String ACTION_CLEANUP_DELETE = "CLEANUP_DELETE";
  private static final String ACTION_CONSISTENCY_MARK_MISSING = "CONSISTENCY_MARK_MISSING";
  private static final String ACTION_CONSISTENCY_DELETE_ORPHAN = "CONSISTENCY_DELETE_ORPHAN";

  private final EvalRunService evalRunService;
  private final ObjectMapper objectMapper;
  private final EvalExportTaskRepository evalExportTaskRepository;
  private final EvalExportAuditRepository evalExportAuditRepository;

  @Value("${eval.export.cleanup.retention-days:7}")
  private long retentionDays;

  @Value("${eval.export.cleanup.enabled:true}")
  private boolean cleanupEnabled;

  @Value("${eval.export.monitor.failure-rate-alert-threshold:0.3}")
  private double failureRateAlertThreshold;

  @Value("${eval.export.monitor.retry-rate-alert-threshold:0.2}")
  private double retryRateAlertThreshold;

  @Value("${eval.export.monitor.pending-alert-minutes:10}")
  private long pendingAlertMinutes;

  public EvalExportService(
      EvalRunService evalRunService,
      ObjectMapper objectMapper,
      EvalExportTaskRepository evalExportTaskRepository,
      EvalExportAuditRepository evalExportAuditRepository
  ) {
    this.evalRunService = evalRunService;
    this.objectMapper = objectMapper;
    this.evalExportTaskRepository = evalExportTaskRepository;
    this.evalExportAuditRepository = evalExportAuditRepository;
  }

  @Transactional
  public ExportTaskResponse createRunCompareExportTask(
      Long taskId,
      Long baselineRunId,
      Long targetRunId,
      Boolean changedOnly,
      String format,
      String operator,
      String source,
      String sourceIp
  ) {
    String normalizedFormat = normalizeFormat(format);
    boolean changedOnlyValue = changedOnly == null || changedOnly;
    String normalizedOperator = normalizeOperator(operator);
    String normalizedSource = normalizeSource(source);
    String normalizedSourceIp = normalizeSourceIp(sourceIp);

    EvalExportTask task = new EvalExportTask();
    task.setTaskId(taskId);
    task.setBaselineRunId(baselineRunId);
    task.setTargetRunId(targetRunId);
    task.setChangedOnly(changedOnlyValue);
    task.setFormat(normalizedFormat);
    task.setStatus(ExportTaskStatus.PENDING);
    task.setMessage("任务已创建");
    task.setCreatedBy(normalizedOperator);
    task.setSource(normalizedSource);
    task.setSourceIp(normalizedSourceIp);
    task.setLastOperator(normalizedOperator);
    task.setLastOperationAt(LocalDateTime.now());

    EvalExportTask saved = evalExportTaskRepository.save(task);
    saveAudit(saved.getExportId(), ACTION_CREATE, normalizedOperator, normalizedSourceIp, "创建导出任务");
    generateRunCompareExportAsync(saved.getExportId());
    return toResponse(saved);
  }

  @Transactional
  public ExportTaskResponse createRunCompareExportTask(
      Long taskId,
      Long baselineRunId,
      Long targetRunId,
      Boolean changedOnly,
      String format
  ) {
    return createRunCompareExportTask(taskId, baselineRunId, targetRunId, changedOnly, format, "system", "api", "-");
  }

  @Transactional(readOnly = true)
  public ExportTaskResponse getExportTask(Long exportId) {
    EvalExportTask task = getTaskOrThrow(exportId);
    return toResponse(task);
  }

  @Transactional(readOnly = true)
  public ExportTaskPageResponse listExportTasks(Long taskId, String status, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    ExportTaskStatus statusFilter = parseExportStatus(status);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "exportId"));

    Page<EvalExportTask> taskPage;
    if (taskId != null && statusFilter != null) {
      taskPage = evalExportTaskRepository.findByTaskIdAndStatus(taskId, statusFilter, pageable);
    } else if (taskId != null) {
      taskPage = evalExportTaskRepository.findByTaskId(taskId, pageable);
    } else if (statusFilter != null) {
      taskPage = evalExportTaskRepository.findByStatus(statusFilter, pageable);
    } else {
      taskPage = evalExportTaskRepository.findAll(pageable);
    }

    List<ExportTaskResponse> items = taskPage.getContent().stream().map(this::toResponse).toList();

    return new ExportTaskPageResponse(
        items,
        taskPage.getNumber(),
        taskPage.getSize(),
        taskPage.getTotalElements(),
        taskPage.getTotalPages(),
        taskPage.hasNext()
    );
  }

  @Transactional
  public ExportTaskResponse retryExportTask(Long exportId, String operator, String sourceIp) {
    EvalExportTask task = getTaskOrThrow(exportId);
    if (task.getStatus() != ExportTaskStatus.FAILED) {
      throw new IllegalStateException("仅 FAILED 状态导出任务允许重试: exportId=" + exportId);
    }

    String normalizedOperator = normalizeOperator(operator);
    String normalizedSourceIp = normalizeSourceIp(sourceIp);

    task.setStatus(ExportTaskStatus.PENDING);
    task.setFileName(null);
    task.setFilePath(null);
    task.setCompletedAt(null);
    task.setMessage("导出重试中");
    task.setLastOperator(normalizedOperator);
    task.setLastOperationAt(LocalDateTime.now());
    EvalExportTask saved = evalExportTaskRepository.save(task);

    saveAudit(saved.getExportId(), ACTION_RETRY, normalizedOperator, normalizedSourceIp, "失败任务重试");

    generateRunCompareExportAsync(saved.getExportId());
    return toResponse(saved);
  }

  @Transactional
  public void deleteExportTask(Long exportId, String operator, String sourceIp) {
    EvalExportTask task = getTaskOrThrow(exportId);
    String normalizedOperator = normalizeOperator(operator);
    String normalizedSourceIp = normalizeSourceIp(sourceIp);
    deleteExportFileIfExists(task.getFilePath());
    evalExportTaskRepository.delete(task);
    saveAudit(exportId, ACTION_DELETE, normalizedOperator, normalizedSourceIp, "删除导出任务");
  }

  @Transactional
  public ExportTaskBatchDeleteResponse batchDeleteExportTasks(List<Long> exportIds, String operator, String sourceIp) {
    if (exportIds == null || exportIds.isEmpty()) {
      return new ExportTaskBatchDeleteResponse(0, 0, 0, List.of(), List.of());
    }

    List<Long> deletedIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (Long exportId : exportIds) {
      if (exportId == null) {
        errors.add("导出任务ID不能为空");
        continue;
      }
      try {
        deleteExportTask(exportId, operator, sourceIp);
        deletedIds.add(exportId);
      } catch (Exception ex) {
        errors.add("#" + exportId + " -> " + ex.getMessage());
      }
    }

    return new ExportTaskBatchDeleteResponse(
        exportIds.size(),
        deletedIds.size(),
        errors.size(),
        deletedIds,
        errors
    );
  }

  @Transactional(readOnly = true)
  public ExportAuditPageResponse listExportAudits(Long exportId, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "auditId"));
    Page<EvalExportAudit> auditPage = evalExportAuditRepository.findByExportIdOrderByAuditIdDesc(exportId, pageable);

    List<ExportAuditResponse> items = auditPage.getContent().stream().map(this::toAuditResponse).toList();
    return new ExportAuditPageResponse(
        items,
        auditPage.getNumber(),
        auditPage.getSize(),
        auditPage.getTotalElements(),
        auditPage.getTotalPages(),
        auditPage.hasNext()
    );
  }

  @Transactional
  public ExportConsistencyResponse scanExportConsistency(boolean repair, String operator, String sourceIp) {
    Path dir = getExportDirectory();
    List<EvalExportTask> tasks = evalExportTaskRepository.findAll();
    Set<String> referencedFiles = new HashSet<>();

    for (EvalExportTask task : tasks) {
      if (task.getFilePath() != null && !task.getFilePath().isBlank()) {
        referencedFiles.add(normalizePath(task.getFilePath()));
      }
    }

    List<Long> missingFileExportIds = tasks.stream()
        .filter(item -> item.getStatus() == ExportTaskStatus.SUCCEEDED)
        .filter(item -> item.getFilePath() == null || item.getFilePath().isBlank() || !Files.exists(Paths.get(item.getFilePath())))
        .map(EvalExportTask::getExportId)
        .sorted(Comparator.naturalOrder())
        .toList();

    List<String> orphanFiles = new ArrayList<>();
    if (Files.exists(dir)) {
      try (var stream = Files.list(dir)) {
        orphanFiles = stream
            .filter(Files::isRegularFile)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .filter(path -> !referencedFiles.contains(path))
            .sorted()
            .toList();
      } catch (IOException ex) {
        throw new IllegalStateException("扫描导出目录失败: " + ex.getMessage(), ex);
      }
    }

    int repairedMissing = 0;
    int removedOrphans = 0;
    String normalizedOperator = normalizeOperator(operator);
    String normalizedSourceIp = normalizeSourceIp(sourceIp);

    if (repair) {
      for (Long exportId : missingFileExportIds) {
        EvalExportTask task = getTaskOrThrow(exportId);
        task.setStatus(ExportTaskStatus.FAILED);
        task.setFileName(null);
        task.setFilePath(null);
        task.setMessage("导出文件缺失，已自动标记失败");
        task.setLastOperator(normalizedOperator);
        task.setLastOperationAt(LocalDateTime.now());
        evalExportTaskRepository.save(task);
        saveAudit(task.getExportId(), ACTION_CONSISTENCY_MARK_MISSING, normalizedOperator, normalizedSourceIp, "修复缺失文件任务");
        repairedMissing++;
      }

      for (String orphanFile : orphanFiles) {
        if (deleteExportFileIfExists(orphanFile)) {
          removedOrphans++;
          saveAudit(0L, ACTION_CONSISTENCY_DELETE_ORPHAN, normalizedOperator, normalizedSourceIp, "删除孤儿文件: " + orphanFile);
        }
      }
    }

    return new ExportConsistencyResponse(
        LocalDateTime.now(),
        tasks.size(),
        countFiles(dir),
        missingFileExportIds.size(),
        orphanFiles.size(),
        repairedMissing,
        removedOrphans,
        missingFileExportIds,
        orphanFiles
    );
  }

  @Transactional(readOnly = true)
  public ExportMonitorMetricsResponse getExportMonitorMetrics(int hours) {
    int safeHours = Math.min(Math.max(hours, 1), 24 * 30);
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime fromTime = now.minusHours(safeHours);

    List<EvalExportTask> tasks = evalExportTaskRepository.findByCreatedAtAfter(fromTime);
    List<EvalExportAudit> audits = evalExportAuditRepository.findByCreatedAtAfter(fromTime);

    long total = tasks.size();
    long succeeded = tasks.stream().filter(item -> item.getStatus() == ExportTaskStatus.SUCCEEDED).count();
    long failed = tasks.stream().filter(item -> item.getStatus() == ExportTaskStatus.FAILED).count();
    long pending = tasks.stream().filter(item -> item.getStatus() == ExportTaskStatus.PENDING).count();
    long running = tasks.stream().filter(item -> item.getStatus() == ExportTaskStatus.RUNNING).count();

    long retried = audits.stream().filter(item -> ACTION_RETRY.equals(item.getAction())).count();
    long cleanedUp = audits.stream().filter(item -> ACTION_CLEANUP_DELETE.equals(item.getAction())).count();
    long deleted = audits.stream().filter(item -> ACTION_DELETE.equals(item.getAction())).count();
    long consistencyRepaired = audits.stream().filter(item ->
        ACTION_CONSISTENCY_MARK_MISSING.equals(item.getAction()) || ACTION_CONSISTENCY_DELETE_ORPHAN.equals(item.getAction())
    ).count();

    double successRate = ratio(succeeded, total);
    double failureRate = ratio(failed, total);
    double retryRate = ratio(retried, total);

    List<String> alerts = new ArrayList<>();
    if (failureRate > failureRateAlertThreshold) {
      alerts.add("失败率过高: " + percent(failureRate));
    }
    if (retryRate > retryRateAlertThreshold) {
      alerts.add("重试率过高: " + percent(retryRate));
    }

    LocalDateTime pendingDeadline = now.minusMinutes(Math.max(pendingAlertMinutes, 1));
    long pendingTooLong = evalExportTaskRepository.findByStatusAndCreatedAtBefore(ExportTaskStatus.PENDING, pendingDeadline).size();
    if (pendingTooLong > 0) {
      alerts.add("存在长时间未处理的 PENDING 任务: " + pendingTooLong);
    }

    return new ExportMonitorMetricsResponse(
        fromTime,
        now,
        total,
        succeeded,
        failed,
        pending,
        running,
        retried,
        cleanedUp,
        deleted,
        consistencyRepaired,
        successRate,
        failureRate,
        retryRate,
        alerts
    );
  }

  @Transactional(readOnly = true)
  public Resource loadExportFile(Long exportId) {
    EvalExportTask task = getTaskOrThrow(exportId);
    if (task.getStatus() != ExportTaskStatus.SUCCEEDED || task.getFilePath() == null) {
      throw new IllegalStateException("导出任务尚未完成: exportId=" + exportId);
    }

    Path path = Paths.get(task.getFilePath());
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("导出文件不存在: exportId=" + exportId);
    }
    return new FileSystemResource(path);
  }

  @Transactional
  @Async
  public void generateRunCompareExportAsync(Long exportId) {
    EvalExportTask task = getTaskOrThrow(exportId);
    task.setStatus(ExportTaskStatus.RUNNING);
    task.setMessage("导出处理中");
    task.setLastOperator("system-async");
    task.setLastOperationAt(LocalDateTime.now());
    evalExportTaskRepository.save(task);
    saveAudit(task.getExportId(), ACTION_START, "system-async", task.getSourceIp(), "异步导出开始");

    try {
      RunCompareResponse compare = evalRunService.compareRuns(
          task.getTaskId(),
          task.getBaselineRunId(),
          task.getTargetRunId(),
          task.getChangedOnly()
      );

      Path dir = Paths.get("target", "exports");
      Files.createDirectories(dir);

      String extension = task.getFormat().equals("csv") ? "csv" : "json";
      String fileName = "run-compare-" + task.getBaselineRunId() + "-vs-" + task.getTargetRunId() + "-" + exportId + "." + extension;
      Path filePath = dir.resolve(fileName);

      String content = task.getFormat().equals("csv") ? toCsv(compare) : toJson(compare);
      Files.writeString(filePath, content, StandardCharsets.UTF_8);

      task.setStatus(ExportTaskStatus.SUCCEEDED);
      task.setFileName(fileName);
      task.setFilePath(filePath.toAbsolutePath().toString());
      task.setMessage("导出完成");
      task.setCompletedAt(LocalDateTime.now());
      task.setLastOperator("system-async");
      task.setLastOperationAt(LocalDateTime.now());
      evalExportTaskRepository.save(task);
      saveAudit(task.getExportId(), ACTION_SUCCESS, "system-async", task.getSourceIp(), "导出成功");
    } catch (Exception ex) {
      EvalExportTask failed = getTaskOrThrow(exportId);
      failed.setStatus(ExportTaskStatus.FAILED);
      failed.setMessage("导出失败: " + ex.getMessage());
      failed.setCompletedAt(LocalDateTime.now());
      failed.setLastOperator("system-async");
      failed.setLastOperationAt(LocalDateTime.now());
      evalExportTaskRepository.save(failed);
      saveAudit(failed.getExportId(), ACTION_FAILED, "system-async", failed.getSourceIp(), "导出失败: " + ex.getMessage());
    }
  }

  private String toJson(RunCompareResponse compare) throws IOException {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compare);
  }

  private String toCsv(RunCompareResponse compare) {
    StringBuilder builder = new StringBuilder();
    builder.append("section,metric,baseline,target,delta,index,input,baselineOutput,targetOutput,baselineError,targetError,changed").append('\n');

    for (RunMetricDiffResponse item : compare.metricDiffs()) {
      builder.append(row(List.of(
          "metric",
          item.metric(),
          item.baseline(),
          item.target(),
          item.delta(),
          "",
          "",
          "",
          "",
          "",
          "",
          ""
      ))).append('\n');
    }

    for (RunSampleDiffResponse item : compare.sampleDiffs()) {
      builder.append(row(List.of(
          "sample",
          "",
          "",
          "",
          "",
          item.index(),
          item.input(),
          item.baselineOutput(),
          item.targetOutput(),
          item.baselineError(),
          item.targetError(),
          item.changed()
      ))).append('\n');
    }
    return builder.toString();
  }

  private String row(List<Object> values) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(cell(values.get(i)));
    }
    return builder.toString();
  }

  private String cell(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return '"' + text.replace("\"", "\"\"") + '"';
  }

  private String normalizeFormat(String format) {
    if (format == null || format.isBlank()) {
      return "json";
    }
    String normalized = format.trim().toLowerCase();
    if (!normalized.equals("json") && !normalized.equals("csv")) {
      throw new IllegalArgumentException("非法导出格式: " + format + "，仅支持 json/csv");
    }
    return normalized;
  }

  private boolean deleteExportFileIfExists(String filePath) {
    if (filePath == null || filePath.isBlank()) {
      return false;
    }
    try {
      return Files.deleteIfExists(Paths.get(filePath));
    } catch (Exception ignored) {
      // ignore file delete errors to avoid affecting metadata cleanup
      return false;
    }
  }

  private ExportTaskStatus parseExportStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return ExportTaskStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("非法导出任务状态过滤参数: " + status);
    }
  }

  private EvalExportTask getTaskOrThrow(Long exportId) {
    return evalExportTaskRepository.findById(exportId)
        .orElseThrow(() -> new IllegalArgumentException("导出任务不存在: exportId=" + exportId));
  }

  private ExportTaskResponse toResponse(EvalExportTask task) {
    String downloadUrl = task.getStatus() == ExportTaskStatus.SUCCEEDED
        ? "/api/eval/exports/" + task.getExportId() + "/download"
        : null;
    return new ExportTaskResponse(
        task.getExportId(),
        task.getTaskId(),
        task.getBaselineRunId(),
        task.getTargetRunId(),
        task.getFormat(),
        task.getStatus().name(),
        task.getFileName(),
        downloadUrl,
        task.getMessage(),
        task.getCreatedBy(),
        task.getSource(),
        task.getSourceIp(),
        task.getLastOperator(),
        task.getLastOperationAt(),
        task.getCreatedAt(),
        task.getCompletedAt()
    );
  }

  private ExportAuditResponse toAuditResponse(EvalExportAudit audit) {
    return new ExportAuditResponse(
        audit.getAuditId(),
        audit.getExportId(),
        audit.getAction(),
        audit.getOperator(),
        audit.getSourceIp(),
        audit.getDetail(),
        audit.getCreatedAt()
    );
  }

  private void saveAudit(Long exportId, String action, String operator, String sourceIp, String detail) {
    EvalExportAudit audit = new EvalExportAudit();
    audit.setExportId(exportId == null ? 0L : exportId);
    audit.setAction(action);
    audit.setOperator(normalizeOperator(operator));
    audit.setSourceIp(normalizeSourceIp(sourceIp));
    audit.setDetail(cut(detail, 1024));
    evalExportAuditRepository.save(audit);
  }

  private String normalizeOperator(String operator) {
    if (operator == null || operator.isBlank()) {
      return "system";
    }
    return cut(operator.trim(), 128);
  }

  private String normalizeSource(String source) {
    if (source == null || source.isBlank()) {
      return "api";
    }
    return cut(source.trim(), 64);
  }

  private String normalizeSourceIp(String sourceIp) {
    if (sourceIp == null || sourceIp.isBlank()) {
      return "-";
    }
    return cut(sourceIp.trim(), 128);
  }

  private String cut(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() > max ? value.substring(0, max) : value;
  }

  private double ratio(long numerator, long denominator) {
    if (denominator <= 0) {
      return 0D;
    }
    return round4((double) numerator / denominator);
  }

  private double round4(double value) {
    return Math.round(value * 10000D) / 10000D;
  }

  private String percent(double ratio) {
    return Math.round(ratio * 10000D) / 100D + "%";
  }

  private Path getExportDirectory() {
    return Paths.get("target", "exports").toAbsolutePath().normalize();
  }

  private String normalizePath(String path) {
    return Paths.get(path).toAbsolutePath().normalize().toString();
  }

  private long countFiles(Path dir) {
    if (!Files.exists(dir)) {
      return 0;
    }
    try (var stream = Files.list(dir)) {
      return stream.filter(Files::isRegularFile).count();
    } catch (IOException ex) {
      return 0;
    }
  }

  @Transactional
  @Scheduled(fixedDelayString = "${eval.export.cleanup.interval-ms:3600000}")
  public void cleanupExpiredTasks() {
    if (!cleanupEnabled || retentionDays < 0) {
      return;
    }
    LocalDateTime deadline = LocalDateTime.now().minusDays(retentionDays);
    List<EvalExportTask> expired = evalExportTaskRepository.findByStatusInAndCompletedAtBefore(
        List.of(ExportTaskStatus.SUCCEEDED, ExportTaskStatus.FAILED),
        deadline
    );
    expired.forEach(item -> {
      deleteExportFileIfExists(item.getFilePath());
      saveAudit(item.getExportId(), ACTION_CLEANUP_DELETE, "system-cleaner", item.getSourceIp(), "定时清理到期导出任务");
    });
    evalExportTaskRepository.deleteAll(expired);
  }
}
