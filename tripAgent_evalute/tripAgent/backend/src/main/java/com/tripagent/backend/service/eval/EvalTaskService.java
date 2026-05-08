package com.tripagent.backend.service.eval;

import com.tripagent.backend.dto.eval.CreateEvalTaskRequest;
import com.tripagent.backend.dto.eval.EvalRunResponse;
import com.tripagent.backend.dto.eval.EvalTaskResponse;
import com.tripagent.backend.dto.eval.TaskRunsPageResponse;
import com.tripagent.backend.dto.eval.UpdateEvalTaskRequest;
import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.enums.TaskStatus;
import com.tripagent.backend.repository.EvalTaskRepository;
import java.util.ArrayList;
import java.util.Optional;
import com.tripagent.backend.service.eval.EvalTaskStatusService;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalTaskService {

  private final EvalTaskRepository evalTaskRepository;
  private final EvalRunService evalRunService;
  private final EvalTaskStatusService evalTaskStatusService;
  private final EntityManager entityManager;

  public EvalTaskService(EvalTaskRepository evalTaskRepository, EvalRunService evalRunService,
                         EvalTaskStatusService evalTaskStatusService,
                         EntityManager entityManager) {
    this.evalTaskRepository = evalTaskRepository;
    this.evalRunService = evalRunService;
    this.evalTaskStatusService = evalTaskStatusService;
    this.entityManager = entityManager;
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
    task.setStrategyConfig(request.strategyConfig());
    task.setStrategyVersion(request.strategyVersion());
    task.setStatus(TaskStatus.READY);

    EvalTask saved = evalTaskRepository.save(task);
    return toTaskResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<EvalTaskResponse> listTasks(String status, String agentVersion) {
    TaskStatus parsedStatus = parseTaskStatus(status);

    // 为避免读取已过期的 task.status，这里先遍历所有任务，触发一个单独事务刷新状态（REQUIRES_NEW），
    // 然后重新读取最新状态并按过滤条件返回响应。
    List<EvalTask> allTasks = evalTaskRepository.findAll();
    List<EvalTaskResponse> responses = new ArrayList<>();

    for (EvalTask t : allTasks) {
      Long taskId = t.getTaskId();
      try {
        evalTaskStatusService.refreshTaskStatus(taskId);
      } catch (Exception ex) {
        // 刷新失败时忽略，仍然尝试使用现有 task
      }
      // 清除一级缓存中的旧数据，强制从数据库重新查询
      entityManager.clear();
      Optional<EvalTask> refreshed = evalTaskRepository.findById(taskId);
      if (refreshed.isEmpty()) {
        continue;
      }
      EvalTask current = refreshed.get();

      if (parsedStatus != null && current.getStatus() != parsedStatus) {
        continue;
      }
      if (hasText(agentVersion) && !agentVersion.trim().equals(current.getAgentVersion())) {
        continue;
      }
      responses.add(toTaskResponse(current));
    }

    return responses;
  }

  @Transactional(readOnly = true)
  public EvalTaskResponse getTask(Long taskId) {
    try {
      evalTaskStatusService.refreshTaskStatus(taskId);
    } catch (Exception ex) {
      // 详情页优先返回任务信息，状态刷新失败时不阻断查询
    }
    entityManager.clear();
    EvalTask task = getTaskOrThrow(taskId);
    return toTaskResponse(task);
  }

  @Transactional
  public EvalTaskResponse updateTask(Long taskId, UpdateEvalTaskRequest request) {
    EvalTask task = getTaskOrThrow(taskId);

    if (task.getStatus() == TaskStatus.RUNNING) {
      throw new IllegalStateException("RUNNING 状态任务不允许修改关键执行参数");
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

  private EvalTask getTaskOrThrow(Long taskId) {
    return evalTaskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("任务不存在: taskId=" + taskId));
  }

  private TaskStatus parseTaskStatus(String status) {
    if (!hasText(status)) {
      return null;
    }
    try {
      return TaskStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("非法状态过滤参数: " + status);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private EvalTaskResponse toTaskResponse(EvalTask task) {
    return new EvalTaskResponse(
        task.getTaskId(),
        task.getTaskName(),
        task.getAgentVersion(),
        task.getDatasetId(),
        task.getMetricSet(),
        task.getStatus(),
        task.getCreatedAt(),
        task.getEvaluationMode(),
        task.getEvaluationMethod(),
        task.getEvaluationDimensions(),
        task.getStrategyConfig(),
        task.getStrategyVersion()
    );
  }
}
