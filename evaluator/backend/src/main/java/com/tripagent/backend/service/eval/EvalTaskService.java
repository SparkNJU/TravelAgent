package com.tripagent.backend.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.dto.eval.CreateEvalTaskRequest;
import com.tripagent.backend.dto.eval.EvalRunResponse;
import com.tripagent.backend.dto.eval.EvalTaskResponse;
import com.tripagent.backend.dto.eval.TaskRunsPageResponse;
import com.tripagent.backend.dto.eval.UpdateEvalTaskRequest;
import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.enums.ComparisonSamplingStrategy;
import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.TaskStatus;
import com.tripagent.backend.repository.EvalComparisonRepository;
import com.tripagent.backend.repository.EvalRunRepository;
import com.tripagent.backend.repository.EvalStrategyVersionRepository;
import com.tripagent.backend.repository.EvalTaskRepository;
import com.tripagent.backend.repository.MetricSnapshotRepository;
import com.tripagent.backend.repository.ModelRatingRepository;
import com.tripagent.backend.repository.QaRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalTaskService {

  private final EvalTaskRepository evalTaskRepository;
  private final EvalRunService evalRunService;
  private final EvalTaskStatusService evalTaskStatusService;
  private final ModelProfileService modelProfileService;
  private final ObjectMapper objectMapper;
  private final EvalRunRepository evalRunRepository;
  private final QaRecordRepository qaRecordRepository;
  private final EvalComparisonRepository evalComparisonRepository;
  private final ModelRatingRepository modelRatingRepository;
  private final MetricSnapshotRepository metricSnapshotRepository;
  private final EvalStrategyVersionRepository evalStrategyVersionRepository;

  public EvalTaskService(EvalTaskRepository evalTaskRepository, EvalRunService evalRunService,
                         EvalTaskStatusService evalTaskStatusService,
                         ModelProfileService modelProfileService,
                         ObjectMapper objectMapper,
                         EvalRunRepository evalRunRepository,
                         QaRecordRepository qaRecordRepository,
                         EvalComparisonRepository evalComparisonRepository,
                         ModelRatingRepository modelRatingRepository,
                         MetricSnapshotRepository metricSnapshotRepository,
                         EvalStrategyVersionRepository evalStrategyVersionRepository) {
    this.evalTaskRepository = evalTaskRepository;
    this.evalRunService = evalRunService;
    this.evalTaskStatusService = evalTaskStatusService;
    this.modelProfileService = modelProfileService;
    this.objectMapper = objectMapper;
    this.evalRunRepository = evalRunRepository;
    this.qaRecordRepository = qaRecordRepository;
    this.evalComparisonRepository = evalComparisonRepository;
    this.modelRatingRepository = modelRatingRepository;
    this.metricSnapshotRepository = metricSnapshotRepository;
    this.evalStrategyVersionRepository = evalStrategyVersionRepository;
  }

  @Transactional
  public EvalTaskResponse createTask(CreateEvalTaskRequest request) {
    EvalTask task = new EvalTask();
    task.setTaskName(request.taskName().trim());
    task.setAgentVersion(request.agentVersion().trim());
    task.setDatasetId(request.datasetId().trim());
    task.setMetricSet(request.metricSet());
    task.setEvaluationMode(request.evaluationMode());
    task.setEvaluationMethod(request.evaluationMethod());
    task.setEvaluationDimensions(request.evaluationDimensions().trim());
    // weightConfig single source of truth: if a strategy version is referenced, we copy its
    // weightConfig / thresholdConfig into strategyConfig at create time so all subsequent reads
    // (EvalRunService, RatingService) only need to look at task.strategyConfig.
    task.setStrategyConfig(materializeStrategyConfig(request.strategyConfig(), request.strategyVersion()));
    task.setStrategyVersion(request.strategyVersion());
    task.setStatus(TaskStatus.READY);

    applyMultiModelFields(
        task,
        request.selectedModelIds(),
        request.judgeModelId(),
        request.comparisonSamplingStrategy(),
        request.positionSwapEnabled(),
        request.evaluationMethod(),
        true
    );

    EvalTask saved = evalTaskRepository.save(task);
    return toTaskResponse(saved);
  }

  /**
   * Compose the JSON blob stored on EvalTask.strategyConfig so the runtime never needs to fetch
   * EvalStrategyVersion separately. The user-supplied strategyConfig wins; missing weightConfig /
   * thresholdConfig keys are filled in from the referenced strategy version.
   */
  private String materializeStrategyConfig(String userStrategyConfig, Long strategyVersionId) {
    Map<String, Object> root = parseRootJson(userStrategyConfig);
    if (strategyVersionId != null) {
      evalStrategyVersionRepository.findById(strategyVersionId).ifPresent(version -> {
        if (!root.containsKey("weightConfig")) {
          Map<String, Object> versionWeights = parseRootJson(version.getWeightConfig());
          if (!versionWeights.isEmpty()) {
            root.put("weightConfig", versionWeights);
          }
        }
        if (!root.containsKey("thresholdConfig")) {
          Map<String, Object> versionThresholds = parseRootJson(version.getThresholdConfig());
          if (!versionThresholds.isEmpty()) {
            root.put("thresholdConfig", versionThresholds);
          }
        }
      });
    }
    if (root.isEmpty()) {
      return userStrategyConfig;
    }
    try {
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      return userStrategyConfig;
    }
  }

  private Map<String, Object> parseRootJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return new java.util.LinkedHashMap<>();
    }
    try {
      Map<String, Object> parsed = objectMapper.readValue(
          raw, new TypeReference<Map<String, Object>>() {});
      return new java.util.LinkedHashMap<>(parsed);
    } catch (Exception ex) {
      return new java.util.LinkedHashMap<>();
    }
  }

  @Transactional(readOnly = true)
  public List<EvalTaskResponse> listTasks(String status, String agentVersion) {
    TaskStatus parsedStatus = parseTaskStatus(status);
    List<EvalTask> allTasks = evalTaskRepository.findAll();
    List<EvalTaskResponse> responses = new ArrayList<>();

    for (EvalTask task : allTasks) {
      TaskStatus currentStatus = evalTaskStatusService.resolveTaskStatus(task.getTaskId(), task.getStatus());
      if (parsedStatus != null && currentStatus != parsedStatus) {
        continue;
      }
      if (hasText(agentVersion) && !agentVersion.trim().equals(task.getAgentVersion())) {
        continue;
      }
      responses.add(toTaskResponse(task, currentStatus));
    }

    return responses;
  }

  @Transactional(readOnly = true)
  public EvalTaskResponse getTask(Long taskId) {
    EvalTask task = getTaskOrThrow(taskId);
    TaskStatus currentStatus = evalTaskStatusService.resolveTaskStatus(taskId, task.getStatus());
    return toTaskResponse(task, currentStatus);
  }

  @Transactional
  public EvalTaskResponse updateTask(Long taskId, UpdateEvalTaskRequest request) {
    EvalTask task = getTaskOrThrow(taskId);

    if (task.getStatus() == TaskStatus.RUNNING) {
      throw new IllegalStateException("RUNNING tasks cannot be edited");
    }

    if (hasText(request.taskName())) {
      task.setTaskName(request.taskName().trim());
    }
    if (hasText(request.agentVersion())) {
      task.setAgentVersion(request.agentVersion().trim());
    }
    if (hasText(request.datasetId())) {
      task.setDatasetId(request.datasetId().trim());
    }
    if (request.metricSet() != null) {
      task.setMetricSet(request.metricSet());
    }
    if (request.evaluationMode() != null) {
      task.setEvaluationMode(request.evaluationMode());
    }
    if (request.evaluationMethod() != null) {
      task.setEvaluationMethod(request.evaluationMethod());
    }
    if (hasText(request.evaluationDimensions())) {
      task.setEvaluationDimensions(request.evaluationDimensions().trim());
    }
    if (request.strategyConfig() != null) {
      task.setStrategyConfig(request.strategyConfig());
    }
    if (request.strategyVersion() != null) {
      task.setStrategyVersion(request.strategyVersion());
    }

    boolean touchMulti = request.selectedModelIds() != null
        || request.judgeModelId() != null
        || request.comparisonSamplingStrategy() != null
        || request.positionSwapEnabled() != null;
    if (touchMulti) {
      List<Long> selectedIds = request.selectedModelIds() != null
          ? request.selectedModelIds()
          : parseSelectedModelIds(task.getSelectedModelIds());
      Long judgeId = request.judgeModelId() != null ? request.judgeModelId() : task.getJudgeModelId();
      ComparisonSamplingStrategy strategy = request.comparisonSamplingStrategy() != null
          ? request.comparisonSamplingStrategy()
          : task.getComparisonSamplingStrategy();
      Boolean swap = request.positionSwapEnabled() != null
          ? request.positionSwapEnabled()
          : task.getPositionSwapEnabled();
      applyMultiModelFields(task, selectedIds, judgeId, strategy, swap, task.getEvaluationMethod(), false);
    }

    EvalTask saved = evalTaskRepository.save(task);
    return toTaskResponse(saved);
  }

  @Transactional
  public EvalRunResponse startTask(Long taskId) {
    EvalTask task = getTaskOrThrow(taskId);

    task.setStatus(TaskStatus.RUNNING);
    EvalTask savedTask = evalTaskRepository.save(task);
    EvalRunResponse run = evalRunService.createRunForTask(savedTask);
    evalRunService.executeRunAsync(run.runId());
    return run;
  }

  /**
   * Hard-delete a task and every record produced by its runs (qa_record / eval_comparison /
   * model_rating / metric_snapshot / eval_run). Tasks in RUNNING state are rejected — the caller
   * has to wait for the run to finish or fail before deleting.
   */
  @Transactional
  public void deleteTask(Long taskId) {
    EvalTask task = getTaskOrThrow(taskId);
    TaskStatus currentStatus = evalTaskStatusService.resolveTaskStatus(taskId, task.getStatus());
    if (currentStatus == TaskStatus.RUNNING) {
      throw new IllegalStateException("RUNNING task cannot be deleted: taskId=" + taskId);
    }

    List<EvalRun> runs = evalRunRepository.findByTaskTaskIdOrderByRunIdDesc(taskId);
    for (EvalRun run : runs) {
      Long runId = run.getRunId();
      modelRatingRepository.deleteByRunRunId(runId);
      evalComparisonRepository.deleteByRunRunId(runId);
      metricSnapshotRepository.deleteByRunRunId(runId);
      qaRecordRepository.deleteByRunRunId(runId);
    }
    evalRunRepository.deleteByTaskTaskId(taskId);
    evalTaskRepository.delete(task);
  }

  @Transactional(readOnly = true)
  public EvalRunResponse getRun(Long runId) {
    return evalRunService.getRun(runId);
  }

  @Transactional(readOnly = true)
  public TaskRunsPageResponse listTaskRuns(Long taskId, String status, Integer page, Integer size) {
    getTaskOrThrow(taskId);
    int safePage = page == null ? 0 : page;
    int safeSize = size == null ? 20 : size;
    Page<EvalRunResponse> runPage = evalRunService.listRunsByTaskIdPaged(taskId, status, safePage, safeSize);
    return new TaskRunsPageResponse(
        runPage.getContent(),
        runPage.getNumber(),
        runPage.getSize(),
        runPage.getTotalElements(),
        runPage.getTotalPages(),
        runPage.hasNext()
    );
  }

  private void applyMultiModelFields(
      EvalTask task,
      List<Long> selectedModelIds,
      Long judgeModelId,
      ComparisonSamplingStrategy strategy,
      Boolean positionSwapEnabled,
      EvaluationMethod method,
      boolean strictWhenBtIntent
  ) {
    boolean hasSelected = selectedModelIds != null && !selectedModelIds.isEmpty();
    boolean btIntent = (hasSelected && selectedModelIds.size() >= 2) || judgeModelId != null;

    if (btIntent) {
      if (method != EvaluationMethod.JUDGE && method != EvaluationMethod.HYBRID) {
        throw new IllegalArgumentException(
            "evaluationMethod=" + method + " does not support multi-model evaluation; use JUDGE or HYBRID");
      }
      if (hasSelected && selectedModelIds.size() < 2) {
        throw new IllegalArgumentException("selectedModelIds requires at least 2 models");
      }
      if (hasSelected) {
        modelProfileService.resolvePlayers(selectedModelIds);
      }
      if (judgeModelId != null) {
        modelProfileService.resolveJudge(judgeModelId);
      } else if (strictWhenBtIntent && hasSelected) {
        throw new IllegalArgumentException("selectedModelIds was provided but judgeModelId is missing");
      }

      if (judgeModelId != null && hasSelected && selectedModelIds.contains(judgeModelId)) {
        throw new IllegalArgumentException("judgeModelId cannot also appear in selectedModelIds");
      }
    } else if (hasSelected) {
      modelProfileService.resolvePlayers(selectedModelIds);
    }

    task.setSelectedModelIds(serializeIds(selectedModelIds));
    task.setJudgeModelId(judgeModelId);
    task.setComparisonSamplingStrategy(
        strategy != null ? strategy : (btIntent ? ComparisonSamplingStrategy.ALL_PAIRS : null));
    task.setPositionSwapEnabled(
        positionSwapEnabled != null ? positionSwapEnabled : (btIntent ? Boolean.TRUE : null));
  }

  private String serializeIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(ids);
    } catch (Exception ex) {
      throw new IllegalArgumentException("selectedModelIds serialization failed: " + ex.getMessage(), ex);
    }
  }

  private List<Long> parseSelectedModelIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  private EvalTask getTaskOrThrow(Long taskId) {
    return evalTaskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("Task not found: taskId=" + taskId));
  }

  private TaskStatus parseTaskStatus(String status) {
    if (!hasText(status)) {
      return null;
    }
    try {
      return TaskStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid status filter: " + status);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private EvalTaskResponse toTaskResponse(EvalTask task) {
    return toTaskResponse(task, task.getStatus());
  }

  private EvalTaskResponse toTaskResponse(EvalTask task, TaskStatus status) {
    return new EvalTaskResponse(
        task.getTaskId(),
        task.getTaskName(),
        task.getAgentVersion(),
        task.getDatasetId(),
        task.getMetricSet(),
        status,
        task.getCreatedAt(),
        task.getEvaluationMode(),
        task.getEvaluationMethod(),
        task.getEvaluationDimensions(),
        task.getStrategyConfig(),
        task.getStrategyVersion(),
        parseSelectedModelIds(task.getSelectedModelIds()),
        task.getJudgeModelId(),
        task.getComparisonSamplingStrategy(),
        task.getPositionSwapEnabled()
    );
  }
}
