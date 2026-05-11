package com.tripagent.backend.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.backend.config.EvalBtRuntimeProperties;
import com.tripagent.backend.dto.eval.EvalRunResponse;
import com.tripagent.backend.dto.eval.MetricSnapshotResponse;
import com.tripagent.backend.dto.eval.QaRecordResponse;
import com.tripagent.backend.dto.eval.RunCompareResponse;
import com.tripagent.backend.dto.eval.RunMetricDiffResponse;
import com.tripagent.backend.dto.eval.RunSampleDiffResponse;
import com.tripagent.backend.entity.CustomMetric;
import com.tripagent.backend.entity.EvalComparison;
import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.MetricSnapshot;
import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.QaRecord;
import com.tripagent.backend.entity.enums.ComparisonResult;
import com.tripagent.backend.entity.enums.CustomMetricType;
import com.tripagent.backend.entity.enums.EvaluationDimension;
import com.tripagent.backend.entity.enums.EvaluationMethod;
import com.tripagent.backend.entity.enums.EvaluationMode;
import com.tripagent.backend.entity.enums.RunStatus;
import com.tripagent.backend.entity.enums.TaskStatus;
import com.tripagent.backend.repository.CustomMetricRepository;
import com.tripagent.backend.repository.EvalComparisonRepository;
import com.tripagent.backend.repository.EvalRunRepository;
import com.tripagent.backend.repository.EvalTaskRepository;
import com.tripagent.backend.repository.MetricSnapshotRepository;
import com.tripagent.backend.repository.QaRecordRepository;
import com.tripagent.backend.service.AgentGatewayService;
import com.tripagent.backend.service.eval.ragas.RagasGatewayService;
import com.tripagent.backend.service.eval.ragas.RagasScoreResult;
import com.tripagent.backend.service.llm.LlmChatRequest;
import com.tripagent.backend.service.llm.LlmChatResponse;
import com.tripagent.backend.service.llm.LlmGateway;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EvalRunService {

  private static final Logger log = LoggerFactory.getLogger(EvalRunService.class);

  private final EvalRunRepository evalRunRepository;
  private final EvalTaskRepository evalTaskRepository;
  private final QaRecordRepository qaRecordRepository;
  private final MetricSnapshotRepository metricSnapshotRepository;
  private final CustomMetricRepository customMetricRepository;
  private final EvalDatasetLoaderService evalDatasetLoaderService;
  private final AgentGatewayService agentGatewayService;
  private final EvalTaskStatusService evalTaskStatusService;
  private final ObjectMapper objectMapper;
  private final ModelProfileService modelProfileService;
  private final LlmGateway llmGateway;
  private final ComparisonSamplerService comparisonSamplerService;
  private final PairwiseJudgeService pairwiseJudgeService;
  private final PerformanceComparator performanceComparator;
  private final EvalComparisonRepository evalComparisonRepository;
  private final RatingService ratingService;
  private final RagasGatewayService ragasGatewayService;
  private final EvalBtRuntimeProperties evalBtRuntimeProperties;
  private static final Pattern KEYWORD_SPLIT_PATTERN = Pattern.compile("[+,/;，；、\\s]+");
  private static final String RAGAS_FAITHFULNESS = "faithfulness";
  private static final String RAGAS_ANSWER_CORRECTNESS = "answer_correctness";

  private final Map<Long, CopyOnWriteArrayList<SseEmitter>> runEmitters = new ConcurrentHashMap<>();
  private final Set<Long> cancelRequestedRuns = ConcurrentHashMap.newKeySet();

  public EvalRunService(
      EvalRunRepository evalRunRepository,
      EvalTaskRepository evalTaskRepository,
      QaRecordRepository qaRecordRepository,
      MetricSnapshotRepository metricSnapshotRepository,
      CustomMetricRepository customMetricRepository,
      EvalDatasetLoaderService evalDatasetLoaderService,
      AgentGatewayService agentGatewayService,
      EvalTaskStatusService evalTaskStatusService,
      ObjectMapper objectMapper,
      ModelProfileService modelProfileService,
      LlmGateway llmGateway,
      ComparisonSamplerService comparisonSamplerService,
      PairwiseJudgeService pairwiseJudgeService,
      PerformanceComparator performanceComparator,
      EvalComparisonRepository evalComparisonRepository,
      RatingService ratingService,
      RagasGatewayService ragasGatewayService,
      EvalBtRuntimeProperties evalBtRuntimeProperties
  ) {
    this.evalRunRepository = evalRunRepository;
    this.evalTaskRepository = evalTaskRepository;
    this.qaRecordRepository = qaRecordRepository;
    this.metricSnapshotRepository = metricSnapshotRepository;
    this.customMetricRepository = customMetricRepository;
    this.evalDatasetLoaderService = evalDatasetLoaderService;
    this.agentGatewayService = agentGatewayService;
    this.evalTaskStatusService = evalTaskStatusService;
    this.objectMapper = objectMapper;
    this.modelProfileService = modelProfileService;
    this.llmGateway = llmGateway;
    this.comparisonSamplerService = comparisonSamplerService;
    this.pairwiseJudgeService = pairwiseJudgeService;
    this.performanceComparator = performanceComparator;
    this.evalComparisonRepository = evalComparisonRepository;
    this.ratingService = ratingService;
    this.ragasGatewayService = ragasGatewayService;
    this.evalBtRuntimeProperties = evalBtRuntimeProperties;
  }

  @Transactional
  public EvalRunResponse createRunForTask(EvalTask task) {
    EvalRun run = new EvalRun();
    run.setTask(task);
    run.setStatus(RunStatus.RUNNING);
    run.setStartTime(LocalDateTime.now());
    run.setTotalCount(0);
    run.setSuccessCount(0);
    run.setFailCount(0);

    EvalRun savedRun = evalRunRepository.save(run);
    return toRunResponse(savedRun);
  }

  @Transactional(readOnly = true)
  public EvalRunResponse getRun(Long runId) {
    EvalRun run = getRunOrThrow(runId);
    return toRunResponse(run);
  }

  @Transactional
  public EvalRunResponse requestCancelLatestRunForTask(Long taskId) {
    EvalRun latestRun = evalRunRepository.findTopByTaskTaskIdOrderByRunIdDesc(taskId)
        .orElseThrow(() -> new IllegalArgumentException("No run found for taskId=" + taskId));
    if (latestRun.getStatus() != RunStatus.RUNNING) {
      throw new IllegalStateException("Task is not running: taskId=" + taskId + ", runId=" + latestRun.getRunId());
    }
    cancelRequestedRuns.add(latestRun.getRunId());
    emitRunEvent(latestRun.getRunId(), "run_cancel_requested", Map.of(
        "runId", latestRun.getRunId(),
        "taskId", taskId,
        "timestamp", LocalDateTime.now().toString()
    ));
    return toRunResponse(latestRun);
  }

  @Transactional(readOnly = true)
  public List<EvalRunResponse> listRunsByTaskId(Long taskId) {
    return evalRunRepository.findByTaskTaskIdOrderByRunIdDesc(taskId).stream().map(this::toRunResponse).toList();
  }

  @Transactional(readOnly = true)
  public Page<EvalRunResponse> listRunsByTaskIdPaged(Long taskId, String status, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "runId"));
    RunStatus parsedStatus = parseRunStatus(status);

    Page<EvalRun> runPage = parsedStatus == null
        ? evalRunRepository.findByTaskTaskId(taskId, pageable)
        : evalRunRepository.findByTaskTaskIdAndStatus(taskId, parsedStatus, pageable);

    return runPage.map(this::toRunResponse);
  }

  @Transactional(readOnly = true)
  public List<QaRecordResponse> getRunRecords(Long runId) {
    getRunOrThrow(runId);
    return qaRecordRepository.findByRunRunIdOrderByQaIdAsc(runId).stream().map(this::toQaRecordResponse).toList();
  }

  @Transactional(readOnly = true)
  public MetricSnapshotResponse getRunMetrics(Long runId) {
    EvalRun run = getRunOrThrow(runId);
    return metricSnapshotRepository.findByRunRunId(runId)
      .map(this::toMetricResponse)
      .orElseGet(() -> new MetricSnapshotResponse(
        runId,
        0D,
        0D,
        0D,
        0L,
        0L,
        0L,
        0D,
        0D,
        0D,
        "Metrics are not ready yet. Run status: " + run.getStatus()
      ));
  }

  @Transactional(readOnly = true)
  public RunCompareResponse compareRuns(Long taskId, Long baselineRunId, Long targetRunId, Boolean changedOnly) {
    EvalRun baselineRun = getRunOrThrow(baselineRunId);
    EvalRun targetRun = getRunOrThrow(targetRunId);

    Long baselineTaskId = baselineRun.getTask().getTaskId();
    Long targetTaskId = targetRun.getTask().getTaskId();
    if (!baselineTaskId.equals(taskId) || !targetTaskId.equals(taskId)) {
      throw new IllegalArgumentException("Run does not belong to task: taskId=" + taskId);
    }

    MetricSnapshot baselineSnapshot = metricSnapshotRepository.findByRunRunId(baselineRunId)
        .orElseThrow(() -> new IllegalArgumentException("Metric snapshot not found: runId=" + baselineRunId));
    MetricSnapshot targetSnapshot = metricSnapshotRepository.findByRunRunId(targetRunId)
        .orElseThrow(() -> new IllegalArgumentException("Metric snapshot not found: runId=" + targetRunId));

    List<RunMetricDiffResponse> metricDiffs = new ArrayList<>();
    metricDiffs.add(metricDiff("taskCompletionRate", baselineSnapshot.getTaskCompletionRate(), targetSnapshot.getTaskCompletionRate()));
    metricDiffs.add(metricDiff("toolCorrectnessScore", baselineSnapshot.getToolCorrectnessScore(), targetSnapshot.getToolCorrectnessScore()));
    metricDiffs.add(metricDiff("toolEfficiencyScore", baselineSnapshot.getToolEfficiencyScore(), targetSnapshot.getToolEfficiencyScore()));
    metricDiffs.add(metricDiff("firstTokenP95", baselineSnapshot.getFirstTokenP95(), targetSnapshot.getFirstTokenP95()));
    metricDiffs.add(metricDiff("endToEndP95", baselineSnapshot.getEndToEndP95(), targetSnapshot.getEndToEndP95()));
    metricDiffs.add(metricDiff("totalTokens", baselineSnapshot.getTotalTokens(), targetSnapshot.getTotalTokens()));
    metricDiffs.add(metricDiff("effectivenessScore", baselineSnapshot.getEffectivenessScore(), targetSnapshot.getEffectivenessScore()));
    metricDiffs.add(metricDiff("safetyScore", baselineSnapshot.getSafetyScore(), targetSnapshot.getSafetyScore()));
    metricDiffs.add(metricDiff("performanceScore", baselineSnapshot.getPerformanceScore(), targetSnapshot.getPerformanceScore()));

    List<QaRecord> baselineRecords = qaRecordRepository.findByRunRunIdOrderByQaIdAsc(baselineRunId);
    List<QaRecord> targetRecords = qaRecordRepository.findByRunRunIdOrderByQaIdAsc(targetRunId);

    int total = Math.max(baselineRecords.size(), targetRecords.size());
    int changed = 0;
    List<RunSampleDiffResponse> sampleDiffs = new ArrayList<>();

    boolean includeChangedOnly = changedOnly == null || changedOnly;

    for (int i = 0; i < total; i++) {
      QaRecord baselineRecord = i < baselineRecords.size() ? baselineRecords.get(i) : null;
      QaRecord targetRecord = i < targetRecords.size() ? targetRecords.get(i) : null;

      String input = baselineRecord != null ? baselineRecord.getInput() : (targetRecord == null ? "" : targetRecord.getInput());
      String baselineOutput = baselineRecord == null ? null : baselineRecord.getActualOutput();
      String targetOutput = targetRecord == null ? null : targetRecord.getActualOutput();
      String baselineError = baselineRecord == null ? null : baselineRecord.getErrorMessage();
      String targetError = targetRecord == null ? null : targetRecord.getErrorMessage();

      boolean itemChanged = !stringEquals(baselineOutput, targetOutput) || !stringEquals(baselineError, targetError);
      if (itemChanged) {
        changed++;
      }
      if (!includeChangedOnly || itemChanged) {
        sampleDiffs.add(new RunSampleDiffResponse(
            i + 1,
            input,
            baselineOutput,
            targetOutput,
            baselineError,
            targetError,
            itemChanged
        ));
      }
    }

    return new RunCompareResponse(
        taskId,
        baselineRunId,
        targetRunId,
        total,
        changed,
        metricDiffs,
        sampleDiffs
    );
  }

  public SseEmitter openRunStream(Long runId) {
    EvalRun run = getRunOrThrow(runId);

    SseEmitter emitter = new SseEmitter(0L);
    runEmitters.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(emitter);

    emitter.onCompletion(() -> removeEmitter(runId, emitter));
    emitter.onTimeout(() -> removeEmitter(runId, emitter));
    emitter.onError(ex -> removeEmitter(runId, emitter));

    Map<String, Object> initEvent = new LinkedHashMap<>();
    initEvent.put("eventType", "run_state");
    initEvent.put("runId", runId);
    initEvent.put("status", run.getStatus());
    initEvent.put("timestamp", LocalDateTime.now().toString());
    sendEventToClient(runId, emitter, "run_state", initEvent);

    if (run.getStatus() == RunStatus.SUCCEEDED || run.getStatus() == RunStatus.FAILED) {
      sendEventToClient(runId, emitter, "run_terminated", Map.of("runId", runId, "status", run.getStatus()));
      safeCompleteEmitter(runId, emitter);
    }

    return emitter;
  }

  @Async
  public void executeRunAsync(Long runId) {
    EvalRun run = getRunWithTaskOrThrow(runId);
    Long taskId = run.getTask().getTaskId();
    EvalTask task = run.getTask();

    if (shouldRunBt(task)) {
      executeBtFlow(run);
      return;
    }

    try {
      throwIfRunCancelled(runId);
      emitRunEvent(runId, "run_started", Map.of("runId", runId, "taskId", task.getTaskId()));

      List<EvalDatasetSample> samples = evalDatasetLoaderService.loadSamples(task.getDatasetId());
      int total = samples.size();
      InferenceRuntimeConfig inferenceConfig = parseInferenceRuntimeConfig(task.getStrategyConfig());
      ModelProfile selectedSingleModel = resolveSingleModel(task, inferenceConfig.modelProfileId());

      run.setTotalCount(total);
      evalRunRepository.save(run);

      // Phase 1: invoke agent / model for every sample, collect outputs.
      List<AgentSampleResult> agentResults = new ArrayList<>(total);
      for (int i = 0; i < samples.size(); i++) {
        throwIfRunCancelled(runId);
        EvalDatasetSample sample = samples.get(i);
        emitRunEvent(runId, "sample_start", Map.of("runId", runId, "index", i + 1, "total", total));
        AgentSampleResult agentResult = selectedSingleModel == null
            ? invokeAgentForSample(runId, i + 1, sample)
            : invokeModelForSample(selectedSingleModel, sample, inferenceConfig);
        agentResults.add(agentResult);
        emitRunEvent(runId, "sample_collected", Map.of(
            "runId", runId, "index", i + 1, "total", total
        ));
      }

      // Phase 2: batch Ragas judgement when JUDGE / HYBRID is requested.
      RagasScoreResult ragasResult = null;
      EvaluationMethod method = task.getEvaluationMethod();
      if (method == EvaluationMethod.JUDGE || method == EvaluationMethod.HYBRID) {
        throwIfRunCancelled(runId);
        List<RagasGatewayService.RagasSample> ragasSamples = new ArrayList<>(total);
        for (int i = 0; i < samples.size(); i++) {
          EvalDatasetSample s = samples.get(i);
          ragasSamples.add(new RagasGatewayService.RagasSample(
              s.input(),
              agentResults.get(i).actualOutput(),
              s.expectedOutput() == null ? "" : s.expectedOutput(),
              null
          ));
        }
        emitRunEvent(runId, "ragas_started", Map.of("runId", runId, "samples", total));
        // Default to faithfulness only — answer_correctness needs an embedding model that
        // ModelScope does not host (ada-002), so it would always fall back to NaN.
        ragasResult = ragasGatewayService.score(
            ragasSamples,
            List.of(RAGAS_FAITHFULNESS)
        );
        emitRunEvent(runId, "ragas_done", Map.of(
            "runId", runId,
            "warning", ragasResult.warning() == null ? "" : ragasResult.warning(),
            "mean", ragasResult.mean()
        ));
      }

      // Phase 3: per-sample evaluation using Ragas score + rule-based safety / process checks.
      int success = 0;
      int fail = 0;
      List<Long> firstTokenValues = new ArrayList<>();
      List<Long> endToEndValues = new ArrayList<>();
      long totalTokens = 0;
      int validToolTraceCount = 0;
      int processViolationCount = 0;
      int safeResponseCount = 0;

      for (int i = 0; i < samples.size(); i++) {
        throwIfRunCancelled(runId);
        EvalDatasetSample sample = samples.get(i);
        AgentSampleResult agentResult = agentResults.get(i);
        long firstTokenLatency = agentResult.firstTokenLatencyMs();
        long endToEndLatency = agentResult.endToEndLatencyMs();
        String actualOutput = agentResult.actualOutput();
        String toolTrace = agentResult.toolTrace();
        String tokenUsage = agentResult.tokenUsage();

        Double ragasJudgeScore = ragasSampleScore(ragasResult, i);
        EvaluationResult evaluation = evaluateSample(task, sample, actualOutput, toolTrace, ragasJudgeScore);
        boolean passed = evaluation.passed();

        QaRecord record = new QaRecord();
        record.setRun(run);
        record.setInput(sample.input());
        record.setExpectedOutput(sample.expectedOutput());
        record.setActualOutput(actualOutput);
        record.setToolTrace(toolTrace);
        record.setFirstTokenLatencyMs(firstTokenLatency);
        record.setEndToEndLatencyMs(endToEndLatency);
        record.setTokenUsage(tokenUsage);
        record.setModelProfileId(agentResult.modelProfileId());

        if (passed) {
          success++;
          if (evaluation.safeOutput()) {
            safeResponseCount++;
          }
          if (evaluation.hasValidToolTrace()) {
            validToolTraceCount++;
          }
        } else {
          fail++;
          record.setErrorCode(evaluation.errorCode());
          record.setErrorMessage(evaluation.errorMessage());
          if (!evaluation.hasValidToolTrace() && task.getEvaluationMode() == EvaluationMode.PROCESS) {
            processViolationCount++;
          }
        }

        qaRecordRepository.save(record);

        firstTokenValues.add(firstTokenLatency);
        endToEndValues.add(endToEndLatency);
        totalTokens += estimateTotalTokens(sample.input(), actualOutput);

        run.setSuccessCount(success);
        run.setFailCount(fail);
        evalRunRepository.save(run);

        emitRunEvent(runId, "sample_done", Map.of(
            "runId", runId,
            "index", i + 1,
            "total", total,
            "passed", passed,
            "successCount", success,
            "failCount", fail
        ));
      }

      MetricSnapshot snapshot = metricSnapshotRepository.findByRunRunId(runId).orElseGet(MetricSnapshot::new);
      snapshot.setRun(run);

      double completionRate = total == 0 ? 0D : (double) success / total;
      double toolCorrectness = total == 0 ? 0D : (double) validToolTraceCount / total;
      double processPenalty = total == 0 ? 0D : (double) processViolationCount / total;
      double toolEfficiency = Math.max(0D, 1D - processPenalty);

      double effectivenessScore;
      if (ragasResult != null) {
        Double faith = ragasResult.meanFor(RAGAS_FAITHFULNESS);
        Double correct = ragasResult.meanFor(RAGAS_ANSWER_CORRECTNESS);
        double ragasMean = averageNonNull(faith, correct);
        if (ragasMean <= 0D) {
          // No usable Ragas signal (timeout/NaN/embedding miss) — fall back to deterministic
          // signal so the run still produces a non-zero score for safety/perf weighting.
          ragasMean = (completionRate + toolCorrectness) / 2D;
        }
        if (method == EvaluationMethod.HYBRID) {
          effectivenessScore = round4(0.5D * ragasMean + 0.5D * completionRate);
        } else {
          effectivenessScore = round4(ragasMean);
        }
      } else {
        // DETERMINISTIC: rule-based completion + toolCorrectness as before.
        effectivenessScore = round4((completionRate + toolCorrectness) / 2D);
      }
      double safetyScore = total == 0 ? 0D : round4((double) safeResponseCount / total);
      double performanceScore = round4(1D - Math.min(1D, percentile95(endToEndValues) / 5000D));

      List<Map<String, Object>> customMetricResults = evaluateCustomMetrics(
          task,
          completionRate,
          percentile95(endToEndValues),
          performanceScore,
          totalTokens,
          ragasResult
      );
      if (!customMetricResults.isEmpty()) {
        double customAvg = customMetricResults.stream()
            .mapToDouble(item -> ((Number) item.get("score")).doubleValue())
            .average()
            .orElse(effectivenessScore);
        effectivenessScore = round4((effectivenessScore + customAvg) / 2D);
      }

      StrategyEvalResult strategyEvalResult = applyStrategy(task, effectivenessScore, safetyScore, performanceScore);

      snapshot.setTaskCompletionRate(round4(completionRate));
      snapshot.setToolCorrectnessScore(round4(toolCorrectness));
      snapshot.setToolEfficiencyScore(round4(toolEfficiency));
      snapshot.setFirstTokenP95(percentile95(firstTokenValues));
      snapshot.setEndToEndP95(percentile95(endToEndValues));
      snapshot.setTotalTokens(totalTokens);
      snapshot.setEffectivenessScore(effectivenessScore);
      snapshot.setSafetyScore(safetyScore);
      snapshot.setPerformanceScore(performanceScore);
      snapshot.setJudgeReason(buildJudgeReason(strategyEvalResult, customMetricResults, ragasResult));
      metricSnapshotRepository.save(snapshot);

      throwIfRunCancelled(runId);
      emitRunEvent(runId, "strategy_applied", Map.of(
          "runId", runId,
          "overallScore", strategyEvalResult.overallScore(),
          "passed", strategyEvalResult.passed()
      ));
      task.setStatus(TaskStatus.SUCCEEDED);
      run.setStatus(RunStatus.SUCCEEDED);
      run.setEndTime(LocalDateTime.now());
      evalRunRepository.save(run);
      evalRunRepository.flush();
      evalTaskStatusService.refreshTaskStatus(taskId);

      afterCommit(() -> {
        emitRunEvent(runId, "run_done", Map.of("runId", runId, "status", RunStatus.SUCCEEDED));
        completeRunEmitters(runId);
      });
    } catch (RunCancelledException cancelEx) {
      markRunFailed(taskId, runId, run, cancelEx.getMessage() == null ? "Run cancelled by user" : cancelEx.getMessage());
    } catch (Exception ex) {
      markRunFailed(taskId, runId, run, ex.getMessage());
    } finally {
      clearCancelRequest(runId);
    }
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        action.run();
      }
    });
  }

  private void markRunFailed(Long taskId, Long runId, EvalRun run, String message) {
    run.setStatus(RunStatus.FAILED);
    if (run.getEndTime() == null) {
      run.setEndTime(LocalDateTime.now());
    }
    evalRunRepository.save(run);
    evalRunRepository.flush();
    evalTaskStatusService.refreshTaskStatus(taskId);

    final String safeMessage = (message == null || message.isBlank()) ? "Run failed" : message;
    afterCommit(() -> {
      emitRunEvent(runId, "run_failed", Map.of("runId", runId, "message", safeMessage));
      completeRunEmitters(runId);
    });
  }

  private void throwIfRunCancelled(Long runId) {
    if (runId != null && cancelRequestedRuns.contains(runId)) {
      throw new RunCancelledException("Run cancelled by user");
    }
  }

  private void clearCancelRequest(Long runId) {
    if (runId != null) {
      cancelRequestedRuns.remove(runId);
    }
  }

  private AgentSampleResult invokeAgentForSample(Long runId, int index, EvalDatasetSample sample) {
    long startedAt = System.currentTimeMillis();
    Long firstTokenLatency = null;
    String toolTrace = "[]";
    StringBuilder answerBuilder = new StringBuilder();

    Iterable<String> chunks = agentGatewayService
        .streamAnswer(runId + "-" + index, sample.input())
        .timeout(Duration.ofSeconds(30))
        .toIterable();

    for (String rawChunk : chunks) {
      String chunk = stripSsePrefix(rawChunk);
      if (chunk == null || chunk.isBlank()) {
        continue;
      }
      if ("[DONE]".equals(chunk)) {
        break;
      }
      if (firstTokenLatency == null) {
        firstTokenLatency = System.currentTimeMillis() - startedAt;
      }

      if (chunk.startsWith("[TOOL_TRACE_JSON]")) {
        toolTrace = chunk.substring("[TOOL_TRACE_JSON]".length()).trim();
        continue;
      }

      if (!answerBuilder.isEmpty()) {
        answerBuilder.append('\n');
      }
      answerBuilder.append(chunk);
    }

    long endedAt = System.currentTimeMillis();
    long firstToken = firstTokenLatency == null ? Math.max(1L, endedAt - startedAt) : firstTokenLatency;
    long endToEnd = Math.max(firstToken, endedAt - startedAt);

    String actualOutput = answerBuilder.toString().trim();
    if (actualOutput.isBlank()) {
      actualOutput = "Agent returned empty output";
    }

    long totalTokens = estimateTotalTokens(sample.input(), actualOutput);
    long promptTokens = Math.max(1L, totalTokens / 2);
    long completionTokens = Math.max(1L, totalTokens - promptTokens);

    String tokenUsage = "{\"promptTokens\":" + promptTokens
        + ",\"completionTokens\":" + completionTokens
        + ",\"totalTokens\":" + totalTokens + "}";

    return new AgentSampleResult(actualOutput, toolTrace, firstToken, endToEnd, tokenUsage, null);
  }

  private AgentSampleResult invokeModelForSample(
      ModelProfile model,
      EvalDatasetSample sample,
      InferenceRuntimeConfig inferenceConfig
  ) {
    LlmChatResponse response = llmGateway.invokeProfile(
        model,
        List.of(
        LlmChatRequest.Message.system(BT_PLAYER_SYSTEM_PROMPT),
        LlmChatRequest.Message.user(sample.input())
        ),
        inferenceConfig.temperature(),
        inferenceConfig.maxTokens()
    );
    long latency = response.latencyMs();
    long totalTokens = response.totalTokens();
    if (totalTokens <= 0) {
      totalTokens = estimateTotalTokens(sample.input(), response.text());
    }
    String tokenUsage = "{\"promptTokens\":" + response.promptTokens()
        + ",\"completionTokens\":" + response.completionTokens()
        + ",\"totalTokens\":" + totalTokens + "}";
    return new AgentSampleResult(
        response.text(),
        "[]",
        latency,
        latency,
        tokenUsage,
        model.getModelProfileId()
    );
  }

  private ModelProfile resolveSingleModel(EvalTask task, Long fallbackModelProfileId) {
    List<Long> modelIds = parseSelectedModelIdsList(task.getSelectedModelIds());
    if (modelIds.size() == 1) {
      return modelProfileService.resolvePlayers(modelIds).get(0);
    }
    if (modelIds.isEmpty() && fallbackModelProfileId != null && fallbackModelProfileId > 0) {
      return modelProfileService.resolvePlayers(List.of(fallbackModelProfileId)).get(0);
    }
    return null;
  }

  private InferenceRuntimeConfig parseInferenceRuntimeConfig(String strategyConfig) {
    if (strategyConfig == null || strategyConfig.isBlank()) {
      return new InferenceRuntimeConfig(null, null, null);
    }
    try {
      Map<String, Object> root = objectMapper.readValue(
          strategyConfig, new TypeReference<Map<String, Object>>() {});
      Object inferenceObj = root.get("inference");
      if (!(inferenceObj instanceof Map<?, ?> inferenceMap)) {
        return new InferenceRuntimeConfig(null, null, null);
      }

      Double temperature = null;
      Integer maxTokens = null;
      Long modelProfileId = null;

      Object t = inferenceMap.get("temperature");
      if (t instanceof Number number) {
        temperature = number.doubleValue();
      }

      Object mt = inferenceMap.get("maxTokens");
      if (!(mt instanceof Number)) {
        mt = inferenceMap.get("max_tokens");
      }
      if (mt instanceof Number number) {
        maxTokens = number.intValue();
      }

      Object extraObj = inferenceMap.get("extra");
      if (extraObj instanceof Map<?, ?> extraMap) {
        Object modelIdObj = extraMap.get("modelProfileId");
        if (modelIdObj instanceof Number number) {
          modelProfileId = number.longValue();
        }
      }

      return new InferenceRuntimeConfig(modelProfileId, temperature, maxTokens);
    } catch (Exception ignored) {
      return new InferenceRuntimeConfig(null, null, null);
    }
  }

  private String stripSsePrefix(String rawChunk) {
    if (rawChunk == null) {
      return null;
    }
    String chunk = rawChunk.trim();
    if (chunk.startsWith("data:")) {
      chunk = chunk.substring(5).trim();
    }
    return chunk;
  }

  private EvaluationResult evaluateSample(
      EvalTask task,
      EvalDatasetSample sample,
      String actualOutput,
      String toolTrace,
      Double ragasJudgeScore
  ) {
    double deterministicScore = evaluateDeterministicScore(sample.expectedOutput(), actualOutput);
    double judgeScore = ragasJudgeScore != null ? ragasJudgeScore : 0D;

    double finalScore;
    EvaluationMethod method = task.getEvaluationMethod();
    if (method == EvaluationMethod.DETERMINISTIC) {
      finalScore = deterministicScore;
    } else if (method == EvaluationMethod.JUDGE) {
      finalScore = judgeScore;
    } else {
      finalScore = round4(deterministicScore * 0.5D + judgeScore * 0.5D);
    }

    boolean validToolTrace = hasValidProcessTrace(toolTrace);
    if (task.getEvaluationMode() == EvaluationMode.PROCESS && !validToolTrace) {
      finalScore = round4(finalScore * 0.7D);
    }

    boolean safeOutput = !containsUnsafeWords(actualOutput);
    if (!safeOutput) {
      finalScore = round4(finalScore * 0.5D);
    }

    boolean passed = finalScore >= 0.6D;
    String errorCode = passed ? null : "EVAL_NOT_PASS";
    String errorMessage = passed
        ? null
        : "evaluation did not pass (score=" + finalScore + ", method=" + method
            + ", mode=" + task.getEvaluationMode() + ")";

    return new EvaluationResult(passed, errorCode, errorMessage, validToolTrace, safeOutput);
  }

  private double evaluateDeterministicScore(String expectedOutput, String actualOutput) {
    if (expectedOutput == null || expectedOutput.isBlank()) {
      return actualOutput == null || actualOutput.isBlank() ? 0D : 1D;
    }
    String normalizedOutput = actualOutput == null ? "" : actualOutput.toLowerCase();
    String[] parts = KEYWORD_SPLIT_PATTERN.split(expectedOutput.trim().toLowerCase());
    int total = 0;
    int hit = 0;
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      total++;
      if (normalizedOutput.contains(part.trim())) {
        hit++;
      }
    }
    if (total == 0) {
      return normalizedOutput.isBlank() ? 0D : 1D;
    }
    return round4((double) hit / total);
  }

  private static Double ragasSampleScore(RagasScoreResult ragasResult, int sampleIndex) {
    if (ragasResult == null) {
      return null;
    }
    Double faith = ragasResult.sampleScore(RAGAS_FAITHFULNESS, sampleIndex);
    Double correct = ragasResult.sampleScore(RAGAS_ANSWER_CORRECTNESS, sampleIndex);
    if (faith == null && correct == null) {
      return null;
    }
    return averageNonNull(faith, correct);
  }

  private static double averageNonNull(Double... values) {
    int n = 0;
    double sum = 0D;
    for (Double v : values) {
      if (v != null && !Double.isNaN(v)) {
        sum += v;
        n++;
      }
    }
    return n == 0 ? 0D : sum / n;
  }

  private boolean hasValidProcessTrace(String toolTrace) {
    if (toolTrace == null || toolTrace.isBlank() || "[]".equals(toolTrace.trim())) {
      return false;
    }
    try {
      List<Object> traceList = objectMapper.readValue(toolTrace, new TypeReference<List<Object>>() {
      });
      return !traceList.isEmpty();
    } catch (Exception ex) {
      return false;
    }
  }
  private boolean containsUnsafeWords(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String lower = text.toLowerCase();
    return lower.contains("violence")
        || lower.contains("illegal")
        || lower.contains("hate")
        || lower.contains("extremism");
  }

  private EvalRun getRunOrThrow(Long runId) {
    return evalRunRepository.findById(runId)
        .orElseThrow(() -> new IllegalArgumentException("Run not found: runId=" + runId));
  }

  private EvalRun getRunWithTaskOrThrow(Long runId) {
    return evalRunRepository.findWithTaskByRunId(runId)
        .orElseThrow(() -> new IllegalArgumentException("Run not found: runId=" + runId));
  }

  private RunStatus parseRunStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return RunStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid run status: " + status);
    }
  }

  private void emitRunEvent(Long runId, String eventName, Object payload) {
    CopyOnWriteArrayList<SseEmitter> emitters = runEmitters.get(runId);
    if (emitters == null || emitters.isEmpty()) {
      return;
    }

    for (SseEmitter emitter : emitters) {
      sendEventToClient(runId, emitter, eventName, payload);
    }
  }

  /**
   * Sends one SSE event; if the client has disconnected or the stream is already closed, drops the
   * emitter and does not propagate — async eval must continue and DB state must still commit.
   */
  private void sendEventToClient(Long runId, SseEmitter emitter, String eventName, Object payload) {
    try {
      emitter.send(SseEmitter.event().name(eventName).data(payload));
    } catch (Exception ex) {
      if (log.isDebugEnabled()) {
        log.debug("SSE send skipped (client likely disconnected) runId={} event={}: {}", runId, eventName, ex.toString());
      }
      safeCompleteEmitter(runId, emitter);
    }
  }

  private void safeCompleteEmitter(Long runId, SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception ignored) {
      // already completed / broken
    }
    if (runId != null) {
      removeEmitter(runId, emitter);
    }
  }

  private void completeRunEmitters(Long runId) {
    CopyOnWriteArrayList<SseEmitter> emitters = runEmitters.remove(runId);
    if (emitters == null) {
      return;
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.complete();
      } catch (Exception ignored) {
      }
    }
  }

  private void removeEmitter(Long runId, SseEmitter emitter) {
    CopyOnWriteArrayList<SseEmitter> emitters = runEmitters.get(runId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      runEmitters.remove(runId);
    }
  }

  private Long percentile95(List<Long> values) {
    if (values.isEmpty()) {
      return 0L;
    }
    List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
    int idx = (int) Math.ceil(sorted.size() * 0.95) - 1;
    int safeIndex = Math.max(0, Math.min(idx, sorted.size() - 1));
    return sorted.get(safeIndex);
  }

  private long estimateTotalTokens(String input, String output) {
    int chars = (input == null ? 0 : input.length()) + (output == null ? 0 : output.length());
    return Math.max(1L, Math.round(chars / 1.8D));
  }

  private double round4(double value) {
    return Math.round(value * 10000D) / 10000D;
  }

  private RunMetricDiffResponse metricDiff(String metric, Number baseline, Number target) {
    Double baselineValue = numberToDouble(baseline);
    Double targetValue = numberToDouble(target);
    Double delta = (baselineValue == null || targetValue == null) ? null : round4(targetValue - baselineValue);
    return new RunMetricDiffResponse(metric, baselineValue, targetValue, delta);
  }

  private Double numberToDouble(Number value) {
    if (value == null) {
      return null;
    }
    return round4(value.doubleValue());
  }

  private boolean stringEquals(String left, String right) {
    if (left == null && right == null) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return left.equals(right);
  }

  private List<Map<String, Object>> evaluateCustomMetrics(
      EvalTask task,
      double completionRate,
      long endToEndP95,
      double performanceScore,
      long totalTokens,
      RagasScoreResult ragasResult
  ) {
    List<CustomMetric> activeMetrics = resolveActiveCustomMetrics(task);
    List<Map<String, Object>> results = new ArrayList<>();

    for (CustomMetric metric : activeMetrics) {
      double score = scoreCustomMetric(
          metric, completionRate, endToEndP95, performanceScore, totalTokens, ragasResult
      );
      Double threshold = metric.getThresholdValue();
      boolean passed = threshold == null || score >= threshold;

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("metricId", metric.getCustomMetricId());
      item.put("metricName", metric.getMetricName());
      item.put("metricType", metric.getMetricType());
      item.put("score", round4(score));
      item.put("threshold", threshold);
      item.put("passed", passed);
      results.add(item);
    }

    return results;
  }

  private List<CustomMetric> resolveActiveCustomMetrics(EvalTask task) {
    List<CustomMetric> enabledMetrics = customMetricRepository.findByEnabledTrue();
    if (enabledMetrics.isEmpty()) {
      return List.of();
    }

    if (task.getMetricSet() == null || task.getMetricSet().isBlank()) {
      return enabledMetrics;
    }

    Set<Long> idFilter = parseMetricIdFilter(task.getMetricSet());
    if (idFilter.isEmpty()) {
      return enabledMetrics;
    }

    return enabledMetrics.stream().filter(metric -> idFilter.contains(metric.getCustomMetricId())).toList();
  }

  private Set<Long> parseMetricIdFilter(String metricSet) {
    Set<Long> ids = new HashSet<>();
    try {
      if (metricSet.trim().startsWith("[")) {
        List<Object> raw = objectMapper.readValue(metricSet, new TypeReference<List<Object>>() {
        });
        for (Object v : raw) {
          if (v instanceof Number number) {
            ids.add(number.longValue());
          } else if (v instanceof String str && !str.isBlank()) {
            ids.add(Long.parseLong(str.trim()));
          }
        }
        return ids;
      }

      String[] parts = metricSet.split(",");
      for (String part : parts) {
        if (!part.isBlank()) {
          ids.add(Long.parseLong(part.trim()));
        }
      }
    } catch (Exception ignored) {
      return Set.of();
    }
    return ids;
  }

  private double scoreCustomMetric(
      CustomMetric metric,
      double completionRate,
      long endToEndP95,
      double performanceScore,
      long totalTokens,
      RagasScoreResult ragasResult
  ) {
    CustomMetricType type = metric.getMetricType();
    if (type == null) {
      return completionRate;
    }
    Double threshold = metric.getThresholdValue();

    return switch (type) {
      case RAGAS_FAITHFULNESS -> ragasMetricScore(ragasResult, RAGAS_FAITHFULNESS, completionRate);
      case RAGAS_ANSWER_CORRECTNESS -> ragasMetricScore(ragasResult, RAGAS_ANSWER_CORRECTNESS, completionRate);
      case RULE_LATENCY_P95 -> {
        double budget = threshold != null && threshold > 0 ? threshold : 5000D;
        yield round4(1D - Math.min(1D, endToEndP95 / budget));
      }
      case RULE_TOKEN_BUDGET -> {
        double budget = threshold != null && threshold > 0 ? threshold : 4096D;
        yield round4(1D - Math.min(1D, totalTokens / budget));
      }
      case JUDGE_PROMPT_TEMPLATE -> 0.85D;
      case JUDGE -> 0.88D;
      case DETERMINISTIC -> {
        String logic = metric.getScoringLogic() == null ? "" : metric.getScoringLogic().toLowerCase();
        if (logic.contains("latency")) {
          yield round4(1D - Math.min(1D, endToEndP95 / 5000D));
        }
        if (logic.contains("performance")) {
          yield performanceScore;
        }
        yield completionRate;
      }
    };
  }

  private double ragasMetricScore(RagasScoreResult ragasResult, String metric, double fallback) {
    if (ragasResult == null) {
      return fallback;
    }
    Double mean = ragasResult.meanFor(metric);
    return mean == null ? fallback : round4(mean);
  }

  private StrategyEvalResult applyStrategy(
      EvalTask task,
      double effectivenessScore,
      double safetyScore,
      double performanceScore
  ) {
    Map<String, Double> weights = new LinkedHashMap<>();
    weights.put("effectiveness", 0.5D);
    weights.put("safety", 0.2D);
    weights.put("performance", 0.3D);

    double overallThreshold = 0.75D;
    double safetyMin = 0.7D;

    // Single source of truth: task.strategyConfig (a JSON blob with optional weightConfig /
    // thresholdConfig).
    Map<String, Double> embeddedWeights = parseEmbeddedWeights(task.getStrategyConfig());
    if (!embeddedWeights.isEmpty()) {
      mergeWeightConfigMap(weights, embeddedWeights);
    }
    Map<String, Double> embeddedThresholds = parseEmbeddedThresholds(task.getStrategyConfig());
    overallThreshold = embeddedThresholds.getOrDefault("overallThreshold", overallThreshold);
    safetyMin = embeddedThresholds.getOrDefault("safetyMin", safetyMin);

    double overallScore = round4(
        effectivenessScore * weights.getOrDefault("effectiveness", 0D)
            + safetyScore * weights.getOrDefault("safety", 0D)
            + performanceScore * weights.getOrDefault("performance", 0D)
    );

    boolean passed = overallScore >= overallThreshold && safetyScore >= safetyMin;

    return new StrategyEvalResult(overallScore, passed, weights, overallThreshold, safetyMin);
  }

  private Map<String, Double> parseEmbeddedWeights(String strategyConfig) {
    if (strategyConfig == null || strategyConfig.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> root = objectMapper.readValue(
          strategyConfig, new TypeReference<Map<String, Object>>() {});
      Object weightObj = root.get("weightConfig");
      if (weightObj == null) {
        weightObj = root.get("weights");
      }
      if (!(weightObj instanceof Map<?, ?> map)) {
        return Map.of();
      }
      Map<String, Double> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getValue() instanceof Number number) {
          result.put(String.valueOf(entry.getKey()), number.doubleValue());
        }
      }
      return result;
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private Map<String, Double> parseEmbeddedThresholds(String strategyConfig) {
    if (strategyConfig == null || strategyConfig.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> root = objectMapper.readValue(
          strategyConfig, new TypeReference<Map<String, Object>>() {});
      Object thresholdObj = root.get("thresholdConfig");
      if (!(thresholdObj instanceof Map<?, ?> map)) {
        return Map.of();
      }
      Map<String, Double> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getValue() instanceof Number number) {
          result.put(String.valueOf(entry.getKey()), number.doubleValue());
        }
      }
      return result;
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private void mergeWeightConfigMap(Map<String, Double> base, Map<String, Double> overrides) {
    for (Map.Entry<String, Double> entry : overrides.entrySet()) {
      if (entry.getValue() != null && entry.getValue() >= 0D) {
        base.put(entry.getKey(), entry.getValue());
      }
    }
    double sum = base.values().stream().mapToDouble(Double::doubleValue).sum();
    if (sum <= 0D) {
      base.put("effectiveness", 0.5D);
      base.put("safety", 0.2D);
      base.put("performance", 0.3D);
      return;
    }
    base.replaceAll((k, v) -> round4(v / sum));
  }

  private void mergeWeightConfig(Map<String, Double> base, String weightConfigRaw) {
    Map<String, Double> parsed = parseDoubleMap(weightConfigRaw);
    if (parsed.isEmpty()) {
      return;
    }
    mergeWeightConfigMap(base, parsed);
  }

  @SuppressWarnings("unused") // kept as a JSON helper in case future strategy stores need it
  private Map<String, Double> parseDoubleMap(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> data = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
      });
      Map<String, Double> result = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : data.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof Number number) {
          result.put(entry.getKey(), number.doubleValue());
        } else if (value instanceof String str && !str.isBlank()) {
          result.put(entry.getKey(), Double.parseDouble(str));
        }
      }
      return result;
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private String buildJudgeReason(
      StrategyEvalResult strategyEvalResult,
      List<Map<String, Object>> customMetricResults,
      RagasScoreResult ragasResult
  ) {
    Map<String, Object> reason = new LinkedHashMap<>();
    reason.put("summary", "Applied strategy weights and custom-metric aggregation");
    reason.put("weights", strategyEvalResult.weights());
    reason.put("overallScore", strategyEvalResult.overallScore());
    reason.put("overallThreshold", strategyEvalResult.overallThreshold());
    reason.put("safetyMin", strategyEvalResult.safetyMin());
    reason.put("passed", strategyEvalResult.passed());
    reason.put("customMetrics", customMetricResults);
    if (ragasResult != null) {
      Map<String, Object> ragasInfo = new LinkedHashMap<>();
      ragasInfo.put("mean", ragasResult.mean());
      if (ragasResult.warning() != null) {
        ragasInfo.put("warning", ragasResult.warning());
      }
      reason.put("ragas", ragasInfo);
    }

    try {
      return objectMapper.writeValueAsString(reason);
    } catch (Exception ex) {
      return "{\"summary\":\"judge reason serialization failed\"}";
    }
  }

  private EvalRunResponse toRunResponse(EvalRun run) {
    return new EvalRunResponse(
        run.getRunId(),
        run.getTask().getTaskId(),
        run.getStatus(),
        run.getStartTime(),
        run.getEndTime(),
        run.getTotalCount(),
        run.getSuccessCount(),
        run.getFailCount()
    );
  }

  private QaRecordResponse toQaRecordResponse(QaRecord record) {
    return new QaRecordResponse(
        record.getQaId(),
        record.getRun().getRunId(),
        record.getModelProfileId(),
        record.getInput(),
        record.getExpectedOutput(),
        record.getActualOutput(),
        record.getToolTrace(),
        record.getFirstTokenLatencyMs(),
        record.getEndToEndLatencyMs(),
        record.getTokenUsage(),
        record.getErrorCode(),
        record.getErrorMessage()
    );
  }

  private MetricSnapshotResponse toMetricResponse(MetricSnapshot snapshot) {
    return new MetricSnapshotResponse(
        snapshot.getRun().getRunId(),
        snapshot.getTaskCompletionRate(),
        snapshot.getToolCorrectnessScore(),
        snapshot.getToolEfficiencyScore(),
        snapshot.getFirstTokenP95(),
        snapshot.getEndToEndP95(),
        snapshot.getTotalTokens(),
        snapshot.getEffectivenessScore(),
        snapshot.getSafetyScore(),
        snapshot.getPerformanceScore(),
        snapshot.getJudgeReason()
    );
  }

  private record StrategyEvalResult(
      double overallScore,
      boolean passed,
      Map<String, Double> weights,
      double overallThreshold,
      double safetyMin
  ) {
  }

  private static final class RunCancelledException extends RuntimeException {
    RunCancelledException(String message) {
      super(message);
    }
  }

    private record AgentSampleResult(
      String actualOutput,
      String toolTrace,
      long firstTokenLatencyMs,
      long endToEndLatencyMs,
      String tokenUsage,
      Long modelProfileId
    ) {
    }

  private record EvaluationResult(
      boolean passed,
      String errorCode,
      String errorMessage,
      boolean hasValidToolTrace,
      boolean safeOutput
  ) {
  }

  private record InferenceRuntimeConfig(
      Long modelProfileId,
      Double temperature,
      Integer maxTokens
  ) {
  }

    // ============================================================
    // BT multi-model evaluation flow
    // ============================================================

    private static final String BT_PLAYER_SYSTEM_PROMPT = """
        You are a travel planning assistant.
        Answer the user's travel request directly with a practical itinerary, route, budget,
        transportation, time plan, and cautions when relevant.
        Keep the answer concise and useful, usually 300-600 Chinese characters.
        """;

    /** Returns true when the task has enough configuration to run the BT flow. */
    private boolean shouldRunBt(EvalTask task) {
      EvaluationMethod method = task.getEvaluationMethod();
      if (method != EvaluationMethod.JUDGE && method != EvaluationMethod.HYBRID) {
        return false;
      }
      List<Long> modelIds = parseSelectedModelIdsList(task.getSelectedModelIds());
      if (modelIds.size() < 2) {
        return false;
      }
      return task.getJudgeModelId() != null;
    }

    private List<Long> parseSelectedModelIdsList(String raw) {
      if (raw == null || raw.isBlank()) {
        return List.of();
      }
      try {
        return objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
      } catch (Exception ex) {
        return List.of();
      }
    }

    /**
     * Runs BT evaluation:
     * sample x player -> QaRecord, then pairwise comparisons -> EvalComparison,
     * then RatingService fits Elo/CI ratings.
     */
    private void executeBtFlow(EvalRun run) {
      Long runId = run.getRunId();
      EvalTask task = run.getTask();
      Long taskId = task.getTaskId();
      ExecutorService playerExecutor = null;
      ExecutorService judgeExecutor = null;

      try {
        throwIfRunCancelled(runId);
        emitRunEvent(runId, "run_started", Map.of(
            "runId", runId,
            "taskId", taskId,
            "mode", "BT"
        ));

        List<Long> playerIds = parseSelectedModelIdsList(task.getSelectedModelIds());
        List<ModelProfile> playersUnordered = modelProfileService.resolvePlayers(playerIds);
        // Preserve the user's selected order; repository IN queries do not guarantee order.
        List<ModelProfile> players = new ArrayList<>(playerIds.size());
        for (Long id : playerIds) {
          for (ModelProfile p : playersUnordered) {
            if (id.equals(p.getModelProfileId())) {
              players.add(p);
              break;
            }
          }
        }
        ModelProfile judge = modelProfileService.resolveJudge(task.getJudgeModelId());

        Set<EvaluationDimension> dimensions = EvaluationDimension.parseDimensionSet(task.getEvaluationDimensions());
        if (dimensions.isEmpty()) {
          dimensions.add(EvaluationDimension.EFFECTIVENESS);
        }
        boolean swapEnabled = task.getPositionSwapEnabled() != null && task.getPositionSwapEnabled();
        boolean skipJudgeWhenPlayerFailed = !Boolean.FALSE.equals(
            evalBtRuntimeProperties.getSkipJudgeWhenPlayerFailed());
        int playerParallelism = resolveParallelism(
            evalBtRuntimeProperties.getPlayerParallelism(), players.size());
        int judgeParallelism = resolveParallelism(
            evalBtRuntimeProperties.getJudgeParallelism(), 4);
        playerExecutor = Executors.newFixedThreadPool(playerParallelism);
        judgeExecutor = Executors.newFixedThreadPool(judgeParallelism);

        List<EvalDatasetSample> samples = evalDatasetLoaderService.loadSamples(task.getDatasetId());
        int totalSamples = samples.size();
        int totalPlayerInvocations = totalSamples * players.size();

        List<Long> firstTokenValues = new ArrayList<>();
        List<Long> endToEndValues = new ArrayList<>();
        long totalTokens = 0;
        int success = 0;
        int fail = 0;

        run.setTotalCount(totalPlayerInvocations);
        evalRunRepository.save(run);

        emitRunEvent(runId, "bt_run_config", Map.of(
            "players", players.stream().map(ModelProfile::getModelId).toList(),
            "judgeModel", judge.getModelId(),
            "dimensions", dimensions.stream().map(Enum::name).toList(),
            "positionSwap", swapEnabled,
            "totalSamples", totalSamples,
            "playerParallelism", playerParallelism,
            "judgeParallelism", judgeParallelism,
            "skipJudgeWhenPlayerFailed", skipJudgeWhenPlayerFailed
        ));

        for (int sampleIdx = 0; sampleIdx < totalSamples; sampleIdx++) {
          throwIfRunCancelled(runId);
          EvalDatasetSample sample = samples.get(sampleIdx);
          int oneBased = sampleIdx + 1;

          emitRunEvent(runId, "sample_start", Map.of(
              "runId", runId, "index", oneBased, "total", totalSamples
          ));

          // Phase 1: invoke player models in parallel for this sample.
          List<CompletableFuture<BtPlayerResult>> playerFutures = new ArrayList<>(players.size());
          for (ModelProfile player : players) {
            playerFutures.add(CompletableFuture.supplyAsync(
                () -> invokeBtPlayer(player, sample),
                playerExecutor
            ));
          }

          List<QaRecord> sampleRecords = new ArrayList<>(players.size());
          for (int playerIdx = 0; playerIdx < players.size(); playerIdx++) {
            ModelProfile player = players.get(playerIdx);
            BtPlayerResult playerResult = playerFutures.get(playerIdx).join();
            QaRecord record = buildBtQaRecord(run, sample, player, playerResult);
            QaRecord saved = qaRecordRepository.save(record);
            sampleRecords.add(saved);
            firstTokenValues.add(playerResult.firstTokenLatencyMs());
            endToEndValues.add(playerResult.endToEndLatencyMs());
            totalTokens += playerResult.totalTokensOrEstimate(sample.input());
            if (playerResult.errorCode() == null) {
              success++;
            } else {
              fail++;
            }
            emitRunEvent(runId, "player_done", Map.of(
                "runId", runId, "sampleIndex", oneBased,
                "modelId", player.getModelId(),
                "error", playerResult.errorCode() == null ? "" : playerResult.errorCode()
            ));
          }

          // Phase 2: sample comparison pairs.
          List<int[]> pairs = comparisonSamplerService.allPairs(players.size());

          // Phase 3: run comparisons (judge dimensions in parallel).
          List<BtComparisonPayload> readyComparisons = new ArrayList<>();
          List<CompletableFuture<BtComparisonPayload>> judgeFutures = new ArrayList<>();
          for (int[] pair : pairs) {
            int idxA = pair[0];
            int idxB = pair[1];
            ModelProfile modelA = players.get(idxA);
            ModelProfile modelB = players.get(idxB);
            QaRecord recordA = sampleRecords.get(idxA);
            QaRecord recordB = sampleRecords.get(idxB);

            for (EvaluationDimension dim : dimensions) {
              if (dim == EvaluationDimension.PERFORMANCE) {
                ComparisonResult perfResult = performanceComparator.compare(
                    recordA.getEndToEndLatencyMs(), recordB.getEndToEndLatencyMs());
                readyComparisons.add(new BtComparisonPayload(
                    oneBased, dim, modelA, modelB, recordA, recordB,
                    false, perfResult, null,
                    "performance: latencyA=" + recordA.getEndToEndLatencyMs()
                        + ", latencyB=" + recordB.getEndToEndLatencyMs(),
                    0L, 0L, 0L
                ));
                continue;
              }

              ComparisonResult autoResult = skipJudgeWhenPlayerFailed
                  ? resolveJudgeByPlayerFailure(recordA, recordB)
                  : null;
              if (autoResult != null) {
                String autoReason = buildAutoJudgeReason(modelA, modelB, recordA, recordB, autoResult);
                readyComparisons.add(new BtComparisonPayload(
                    oneBased, dim, modelA, modelB, recordA, recordB,
                    false, autoResult, null, autoReason,
                    0L, 0L, 0L
                ));
                if (swapEnabled) {
                  readyComparisons.add(new BtComparisonPayload(
                      oneBased, dim, modelA, modelB, recordA, recordB,
                      true, autoResult, null, "[swap] " + autoReason,
                      0L, 0L, 0L
                  ));
                }
                continue;
              }

              judgeFutures.add(CompletableFuture.supplyAsync(
                  () -> runJudgeComparison(
                      runId, oneBased, dim, task, sample, judge, modelA, modelB, recordA, recordB, false
                  ),
                  judgeExecutor
              ));

              if (swapEnabled) {
                judgeFutures.add(CompletableFuture.supplyAsync(
                    () -> runJudgeComparison(
                        runId, oneBased, dim, task, sample, judge, modelA, modelB, recordA, recordB, true
                    ),
                    judgeExecutor
                ));
              }
            }
          }

          for (CompletableFuture<BtComparisonPayload> future : judgeFutures) {
            readyComparisons.add(future.join());
          }
          throwIfRunCancelled(runId);
          for (BtComparisonPayload payload : readyComparisons) {
            saveComparison(run, payload.sampleIndex(), payload.dimension(),
                payload.modelA(), payload.modelB(),
                payload.qaA(), payload.qaB(),
                payload.positionSwap(), payload.result(), payload.judgeModelId(),
                payload.reason(), payload.latencyMs(),
                payload.promptTokens(), payload.completionTokens());
          }

          run.setSuccessCount(success);
          run.setFailCount(fail);
          evalRunRepository.save(run);

          emitRunEvent(runId, "sample_done", Map.of(
              "runId", runId,
              "index", oneBased,
              "total", totalSamples,
              "successCount", success,
              "failCount", fail
          ));
        }

        // Store aggregate run metrics before fitting BT ratings.
        MetricSnapshot snapshot = metricSnapshotRepository.findByRunRunId(runId)
            .orElseGet(MetricSnapshot::new);
        snapshot.setRun(run);
        double completionRate = totalPlayerInvocations == 0 ? 0D
            : (double) success / totalPlayerInvocations;
        snapshot.setTaskCompletionRate(round4(completionRate));
        snapshot.setToolCorrectnessScore(0D);
        snapshot.setToolEfficiencyScore(1D);
        snapshot.setFirstTokenP95(percentile95(firstTokenValues));
        snapshot.setEndToEndP95(percentile95(endToEndValues));
        snapshot.setTotalTokens(totalTokens);
        snapshot.setEffectivenessScore(0D);
        snapshot.setSafetyScore(0D);
        snapshot.setPerformanceScore(0D);
        snapshot.setJudgeReason("{\"summary\":\"BT comparisons finished; waiting for Step 6 rating fit\",\"totalComparisons\":"
            + evalComparisonRepository.countByRunRunId(runId) + "}");
        metricSnapshotRepository.save(snapshot);

        // Step 6: fit and persist Elo/CI ratings.
        emitRunEvent(runId, "bt_fitting_started", Map.of("runId", runId));
        try {
          ratingService.computeAndPersist(runId);
        } catch (Exception ratingEx) {
          emitRunEvent(runId, "bt_fitting_failed", Map.of(
              "runId", runId,
              "message", ratingEx.getMessage() == null ? "BT rating fit failed" : ratingEx.getMessage()
          ));
        }
        emitRunEvent(runId, "bt_fitting_done", Map.of("runId", runId));

        task.setStatus(TaskStatus.SUCCEEDED);
        run.setStatus(RunStatus.SUCCEEDED);
        run.setEndTime(LocalDateTime.now());
        evalRunRepository.save(run);
        evalRunRepository.flush();
        evalTaskStatusService.refreshTaskStatus(taskId);

        afterCommit(() -> {
          emitRunEvent(runId, "run_done", Map.of("runId", runId, "status", RunStatus.SUCCEEDED));
          completeRunEmitters(runId);
        });
      } catch (RunCancelledException cancelEx) {
        markRunFailed(taskId, runId, run, cancelEx.getMessage() == null ? "Run cancelled by user" : cancelEx.getMessage());
      } catch (Exception ex) {
        markRunFailed(taskId, runId, run, ex.getMessage() == null ? "BT run failed" : ex.getMessage());
      } finally {
        shutdownExecutor(playerExecutor, "bt-player");
        shutdownExecutor(judgeExecutor, "bt-judge");
        clearCancelRequest(runId);
      }
    }

    private int resolveParallelism(Integer configured, int fallback) {
      int safeFallback = fallback > 0 ? fallback : 1;
      int value = configured == null ? safeFallback : configured;
      if (value <= 0) {
        value = safeFallback;
      }
      return Math.min(value, 16);
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
      if (executor == null) {
        return;
      }
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          List<Runnable> dropped = executor.shutdownNow();
          log.warn("{} executor forced shutdown, droppedTasks={}", name, dropped.size());
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
    }

    private ComparisonResult resolveJudgeByPlayerFailure(QaRecord recordA, QaRecord recordB) {
      boolean failedA = hasEvalError(recordA);
      boolean failedB = hasEvalError(recordB);
      if (!failedA && !failedB) {
        return null;
      }
      if (failedA && failedB) {
        return ComparisonResult.INVALID;
      }
      return failedA ? ComparisonResult.B_PREFERRED : ComparisonResult.A_PREFERRED;
    }

    private boolean hasEvalError(QaRecord record) {
      return record != null && record.getErrorCode() != null && !record.getErrorCode().isBlank();
    }

    private String buildAutoJudgeReason(
        ModelProfile modelA,
        ModelProfile modelB,
        QaRecord recordA,
        QaRecord recordB,
        ComparisonResult result
    ) {
      return switch (result) {
        case A_PREFERRED -> "auto-judge skipped: modelB failed (" + modelB.getModelId()
            + ", error=" + truncateReason(recordB.getErrorCode(), 80) + "), prefer modelA ("
            + modelA.getModelId() + ")";
        case B_PREFERRED -> "auto-judge skipped: modelA failed (" + modelA.getModelId()
            + ", error=" + truncateReason(recordA.getErrorCode(), 80) + "), prefer modelB ("
            + modelB.getModelId() + ")";
        case INVALID -> "auto-judge skipped: both players failed ("
            + modelA.getModelId() + ": " + truncateReason(recordA.getErrorCode(), 60) + ", "
            + modelB.getModelId() + ": " + truncateReason(recordB.getErrorCode(), 60) + ")";
        case TIE -> "auto-judge skipped: tie";
      };
    }

    private BtComparisonPayload runJudgeComparison(
        Long runId,
        int sampleIndex,
        EvaluationDimension dim,
        EvalTask task,
        EvalDatasetSample sample,
        ModelProfile judge,
        ModelProfile modelA,
        ModelProfile modelB,
        QaRecord recordA,
        QaRecord recordB,
        boolean positionSwap
    ) {
      throwIfRunCancelled(runId);
      try {
        PairwiseJudgeService.JudgeOutcome outcome = positionSwap
            ? pairwiseJudgeService.judgeOnce(
                judge, dim, task.getEvaluationMode(), task.getEvaluationMethod(),
                sample.input(), sample.expectedOutput(),
                recordB.getToolTrace(), recordA.getToolTrace(),
                recordB.getActualOutput(), recordA.getActualOutput()
            )
            : pairwiseJudgeService.judgeOnce(
                judge, dim, task.getEvaluationMode(), task.getEvaluationMethod(),
                sample.input(), sample.expectedOutput(),
                recordA.getToolTrace(), recordB.getToolTrace(),
                recordA.getActualOutput(), recordB.getActualOutput()
            );
        throwIfRunCancelled(runId);

        ComparisonResult canonicalResult = positionSwap
            ? flipResult(outcome.result())
            : outcome.result();
        String reason = positionSwap ? "[swap] " + outcome.reason() : outcome.reason();
        return new BtComparisonPayload(
            sampleIndex, dim, modelA, modelB, recordA, recordB,
            positionSwap, canonicalResult, judge.getModelProfileId(),
            reason, outcome.latencyMs(),
            outcome.promptTokens(), outcome.completionTokens()
        );
      } catch (RunCancelledException cancelled) {
        throw cancelled;
      } catch (Exception ex) {
        String reason = "judge task failed: " + truncateReason(ex.getMessage(), 200);
        if (positionSwap) {
          reason = "[swap] " + reason;
        }
        return new BtComparisonPayload(
            sampleIndex, dim, modelA, modelB, recordA, recordB,
            positionSwap, ComparisonResult.INVALID, judge.getModelProfileId(),
            reason, 0L, 0L, 0L
        );
      }
    }

    private BtPlayerResult invokeBtPlayer(ModelProfile player, EvalDatasetSample sample) {
      long start = System.currentTimeMillis();
      try {
        LlmChatResponse response = llmGateway.invokeProfile(player, List.of(
            LlmChatRequest.Message.system(BT_PLAYER_SYSTEM_PROMPT),
            LlmChatRequest.Message.user(sample.input())
        ));
        long endToEnd = response.latencyMs();
        // The non-streaming wrapper only exposes total latency, so use it for first-token latency too.
        long firstToken = endToEnd;
        return new BtPlayerResult(
            response.text(),
            firstToken,
            endToEnd,
            response.promptTokens(),
            response.completionTokens(),
            null,
            null
        );
      } catch (Exception ex) {
        long elapsed = System.currentTimeMillis() - start;
        return new BtPlayerResult(
            "",
            elapsed,
            elapsed,
            0L, 0L,
            "PLAYER_INVOCATION_FAILED",
            ex.getMessage() == null ? "" : ex.getMessage()
        );
      }
    }

    private QaRecord buildBtQaRecord(EvalRun run, EvalDatasetSample sample,
                                     ModelProfile player, BtPlayerResult result) {
      QaRecord record = new QaRecord();
      record.setRun(run);
      record.setInput(sample.input());
      record.setExpectedOutput(sample.expectedOutput());
      String output = (result.actualOutput() == null || result.actualOutput().isBlank())
          ? "(empty output)" : result.actualOutput();
      record.setActualOutput(output);
      record.setToolTrace("[]");
      record.setFirstTokenLatencyMs(result.firstTokenLatencyMs());
      record.setEndToEndLatencyMs(result.endToEndLatencyMs());

      long totalTokens = result.promptTokens() + result.completionTokens();
      if (totalTokens <= 0) {
        totalTokens = estimateTotalTokens(sample.input(), result.actualOutput());
      }
      String tokenUsageJson = "{\"promptTokens\":" + result.promptTokens()
          + ",\"completionTokens\":" + result.completionTokens()
          + ",\"totalTokens\":" + totalTokens + "}";
      record.setTokenUsage(tokenUsageJson);
      record.setErrorCode(result.errorCode());
      record.setErrorMessage(result.errorMessage());
      record.setModelProfileId(player.getModelProfileId());
      return record;
    }

    private void saveComparison(EvalRun run, int sampleIndex, EvaluationDimension dim,
                                ModelProfile modelA, ModelProfile modelB,
                                QaRecord qaA, QaRecord qaB,
                                boolean positionSwap, ComparisonResult result, Long judgeModelId,
                                String reason, long latencyMs,
                                long promptTokens, long completionTokens) {
      EvalComparison comp = new EvalComparison();
      comp.setRun(run);
      comp.setSampleIndex(sampleIndex);
      comp.setDimension(dim);
      comp.setModelAId(modelA.getModelProfileId());
      comp.setModelBId(modelB.getModelProfileId());
      comp.setQaRecordAId(qaA != null ? qaA.getQaId() : null);
      comp.setQaRecordBId(qaB != null ? qaB.getQaId() : null);
      comp.setPositionSwap(positionSwap);
      comp.setResult(result);
      comp.setJudgeModelId(judgeModelId);
      comp.setJudgeReason(truncateReason(reason, 480));
      comp.setJudgeLatencyMs(latencyMs);
      comp.setJudgePromptTokens(promptTokens);
      comp.setJudgeCompletionTokens(completionTokens);
      evalComparisonRepository.save(comp);
    }

    private ComparisonResult flipResult(ComparisonResult result) {
      return switch (result) {
        case A_PREFERRED -> ComparisonResult.B_PREFERRED;
        case B_PREFERRED -> ComparisonResult.A_PREFERRED;
        default -> result;
      };
    }

    private String truncateReason(String s, int max) {
      if (s == null) return null;
      return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record BtComparisonPayload(
        int sampleIndex,
        EvaluationDimension dimension,
        ModelProfile modelA,
        ModelProfile modelB,
        QaRecord qaA,
        QaRecord qaB,
        boolean positionSwap,
        ComparisonResult result,
        Long judgeModelId,
        String reason,
        long latencyMs,
        long promptTokens,
        long completionTokens
    ) {
    }

    private record BtPlayerResult(
        String actualOutput,
        long firstTokenLatencyMs,
        long endToEndLatencyMs,
        long promptTokens,
        long completionTokens,
        String errorCode,
        String errorMessage
    ) {
      long totalTokensOrEstimate(String input) {
        long total = promptTokens + completionTokens;
        if (total > 0) return total;
        int chars = (input == null ? 0 : input.length()) + (actualOutput == null ? 0 : actualOutput.length());
        return Math.max(1L, Math.round(chars / 1.8D));
      }
    }
}

