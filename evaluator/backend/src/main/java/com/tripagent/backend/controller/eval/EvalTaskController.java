package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.CreateEvalTaskRequest;
import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.dto.eval.EvalRunResponse;
import com.tripagent.backend.dto.eval.EvalTaskResponse;
import com.tripagent.backend.dto.eval.ExportAuditPageResponse;
import com.tripagent.backend.dto.eval.ExportTaskBatchDeleteRequest;
import com.tripagent.backend.dto.eval.ExportTaskBatchDeleteResponse;
import com.tripagent.backend.dto.eval.ExportConsistencyResponse;
import com.tripagent.backend.dto.eval.ExportMonitorMetricsResponse;
import com.tripagent.backend.dto.eval.ExportTaskPageResponse;
import com.tripagent.backend.dto.eval.ExportTaskResponse;
import com.tripagent.backend.dto.eval.MetricSnapshotResponse;
import com.tripagent.backend.dto.eval.QaRecordResponse;
import com.tripagent.backend.dto.eval.RunCompareResponse;
import com.tripagent.backend.dto.eval.TaskRunsPageResponse;
import com.tripagent.backend.dto.eval.UpdateEvalTaskRequest;
import com.tripagent.backend.service.eval.EvalExportService;
import com.tripagent.backend.service.eval.EvalRunService;
import com.tripagent.backend.service.eval.EvalTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/eval")
public class EvalTaskController {

  private final EvalTaskService evalTaskService;
  private final EvalRunService evalRunService;
  private final EvalExportService evalExportService;

  public EvalTaskController(
      EvalTaskService evalTaskService,
      EvalRunService evalRunService,
      EvalExportService evalExportService
  ) {
    this.evalTaskService = evalTaskService;
    this.evalRunService = evalRunService;
    this.evalExportService = evalExportService;
  }

  @PostMapping("/tasks")
  public ResponseEntity<EvalApiResponse<EvalTaskResponse>> createTask(
      @Valid @RequestBody CreateEvalTaskRequest request
  ) {
    EvalTaskResponse created = evalTaskService.createTask(request);
    return ResponseEntity.ok(EvalApiResponse.success(created));
  }

  @GetMapping("/tasks")
  public ResponseEntity<EvalApiResponse<List<EvalTaskResponse>>> listTasks(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String agentVersion
  ) {
    List<EvalTaskResponse> tasks = evalTaskService.listTasks(status, agentVersion);
    return ResponseEntity.ok(EvalApiResponse.success(tasks));
  }

  @GetMapping("/tasks/{taskId}")
  public ResponseEntity<EvalApiResponse<EvalTaskResponse>> getTask(@PathVariable Long taskId) {
    EvalTaskResponse task = evalTaskService.getTask(taskId);
    return ResponseEntity.ok(EvalApiResponse.success(task));
  }

  @GetMapping("/tasks/{taskId}/runs")
  public ResponseEntity<EvalApiResponse<TaskRunsPageResponse>> listTaskRuns(
      @PathVariable Long taskId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size
  ) {
    TaskRunsPageResponse runs = evalTaskService.listTaskRuns(taskId, status, page, size);
    return ResponseEntity.ok(EvalApiResponse.success(runs));
  }

  @GetMapping("/tasks/{taskId}/runs/compare")
  public ResponseEntity<EvalApiResponse<RunCompareResponse>> compareTaskRuns(
      @PathVariable Long taskId,
      @RequestParam Long baselineRunId,
      @RequestParam Long targetRunId,
      @RequestParam(required = false, defaultValue = "true") Boolean changedOnly
  ) {
    RunCompareResponse response = evalRunService.compareRuns(taskId, baselineRunId, targetRunId, changedOnly);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @PostMapping("/tasks/{taskId}/runs/compare/export")
  public ResponseEntity<EvalApiResponse<ExportTaskResponse>> createRunCompareExport(
      @PathVariable Long taskId,
      @RequestParam Long baselineRunId,
      @RequestParam Long targetRunId,
      @RequestParam(required = false, defaultValue = "true") Boolean changedOnly,
      @RequestParam(required = false, defaultValue = "json") String format,
      @RequestParam(required = false) String operator,
      @RequestParam(required = false, defaultValue = "api") String source,
      @RequestParam(required = false) String sourceIp,
      HttpServletRequest request
  ) {
    ExportTaskResponse task = evalExportService.createRunCompareExportTask(
        taskId,
        baselineRunId,
        targetRunId,
        changedOnly,
        format,
        operator,
        source,
        resolveSourceIp(request, sourceIp)
    );
    return ResponseEntity.ok(EvalApiResponse.success(task));
  }

  @GetMapping("/exports/{exportId}")
  public ResponseEntity<EvalApiResponse<ExportTaskResponse>> getExportTask(@PathVariable Long exportId) {
    return ResponseEntity.ok(EvalApiResponse.success(evalExportService.getExportTask(exportId)));
  }

  @GetMapping("/exports")
  public ResponseEntity<EvalApiResponse<ExportTaskPageResponse>> listExportTasks(
      @RequestParam(required = false) Long taskId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size
  ) {
    ExportTaskPageResponse response = evalExportService.listExportTasks(taskId, status, page, size);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @PostMapping("/exports/{exportId}/retry")
  public ResponseEntity<EvalApiResponse<ExportTaskResponse>> retryExportTask(
      @PathVariable Long exportId,
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String sourceIp,
      HttpServletRequest request
  ) {
    ExportTaskResponse response = evalExportService.retryExportTask(exportId, operator, resolveSourceIp(request, sourceIp));
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @DeleteMapping("/exports/{exportId}")
  public ResponseEntity<EvalApiResponse<Boolean>> deleteExportTask(
      @PathVariable Long exportId,
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String sourceIp,
      HttpServletRequest request
  ) {
    evalExportService.deleteExportTask(exportId, operator, resolveSourceIp(request, sourceIp));
    return ResponseEntity.ok(EvalApiResponse.success(Boolean.TRUE));
  }

  @PostMapping("/exports/batch-delete")
  public ResponseEntity<EvalApiResponse<ExportTaskBatchDeleteResponse>> batchDeleteExportTasks(
      @RequestBody ExportTaskBatchDeleteRequest request,
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String sourceIp,
      HttpServletRequest httpRequest
  ) {
    ExportTaskBatchDeleteResponse response = evalExportService.batchDeleteExportTasks(
        request.exportIds(),
        operator,
        resolveSourceIp(httpRequest, sourceIp)
    );
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @GetMapping("/exports/{exportId}/audits")
  public ResponseEntity<EvalApiResponse<ExportAuditPageResponse>> listExportAudits(
      @PathVariable Long exportId,
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size
  ) {
    ExportAuditPageResponse response = evalExportService.listExportAudits(exportId, page, size);
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @GetMapping("/exports/consistency-check")
  public ResponseEntity<EvalApiResponse<ExportConsistencyResponse>> scanExportConsistency(
      @RequestParam(required = false, defaultValue = "false") Boolean repair,
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String sourceIp,
      HttpServletRequest request
  ) {
    ExportConsistencyResponse response = evalExportService.scanExportConsistency(
        Boolean.TRUE.equals(repair),
        operator,
        resolveSourceIp(request, sourceIp)
    );
    return ResponseEntity.ok(EvalApiResponse.success(response));
  }

  @GetMapping("/exports/metrics")
  public ResponseEntity<EvalApiResponse<ExportMonitorMetricsResponse>> getExportMonitorMetrics(
      @RequestParam(required = false, defaultValue = "24") Integer hours
  ) {
    return ResponseEntity.ok(EvalApiResponse.success(evalExportService.getExportMonitorMetrics(hours)));
  }

  @GetMapping("/exports/{exportId}/download")
  public ResponseEntity<Resource> downloadExport(@PathVariable Long exportId) {
    ExportTaskResponse task = evalExportService.getExportTask(exportId);
    Resource resource = evalExportService.loadExportFile(exportId);
    String fileName = task.fileName() == null ? ("export-" + exportId + ".dat") : task.fileName();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .body(resource);
  }

  @PutMapping("/tasks/{taskId}")
  public ResponseEntity<EvalApiResponse<EvalTaskResponse>> updateTask(
      @PathVariable Long taskId,
      @RequestBody UpdateEvalTaskRequest request
  ) {
    EvalTaskResponse updated = evalTaskService.updateTask(taskId, request);
    return ResponseEntity.ok(EvalApiResponse.success(updated));
  }

  @PostMapping("/tasks/{taskId}/start")
  public ResponseEntity<EvalApiResponse<EvalRunResponse>> startTask(@PathVariable Long taskId) {
    EvalRunResponse run = evalTaskService.startTask(taskId);
    return ResponseEntity.ok(EvalApiResponse.success(run));
  }

  @PostMapping("/tasks/{taskId}/cancel")
  public ResponseEntity<EvalApiResponse<EvalRunResponse>> cancelTask(@PathVariable Long taskId) {
    EvalRunResponse run = evalTaskService.cancelTask(taskId);
    return ResponseEntity.ok(EvalApiResponse.success(run));
  }

  @DeleteMapping("/tasks/{taskId}")
  public ResponseEntity<EvalApiResponse<Boolean>> deleteTask(@PathVariable Long taskId) {
    evalTaskService.deleteTask(taskId);
    return ResponseEntity.ok(EvalApiResponse.success(Boolean.TRUE));
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<EvalApiResponse<EvalRunResponse>> getRun(@PathVariable Long runId) {
    EvalRunResponse run = evalTaskService.getRun(runId);
    return ResponseEntity.ok(EvalApiResponse.success(run));
  }

  @GetMapping("/runs/{runId}/records")
  public ResponseEntity<EvalApiResponse<List<QaRecordResponse>>> getRunRecords(@PathVariable Long runId) {
    List<QaRecordResponse> records = evalRunService.getRunRecords(runId);
    return ResponseEntity.ok(EvalApiResponse.success(records));
  }

  @GetMapping("/runs/{runId}/metrics")
  public ResponseEntity<EvalApiResponse<MetricSnapshotResponse>> getRunMetrics(@PathVariable Long runId) {
    MetricSnapshotResponse metrics = evalRunService.getRunMetrics(runId);
    return ResponseEntity.ok(EvalApiResponse.success(metrics));
  }

  @GetMapping("/runs/{runId}/stream")
  public SseEmitter streamRun(@PathVariable Long runId) {
    return evalRunService.openRunStream(runId);
  }

  private String resolveSourceIp(HttpServletRequest request, String sourceIp) {
    if (sourceIp != null && !sourceIp.isBlank()) {
      return sourceIp;
    }
    String fromHeader = request.getHeader("X-Forwarded-For");
    if (fromHeader != null && !fromHeader.isBlank()) {
      return fromHeader.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
