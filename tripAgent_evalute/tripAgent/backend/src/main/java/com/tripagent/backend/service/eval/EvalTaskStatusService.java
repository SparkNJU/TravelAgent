package com.tripagent.backend.service.eval;

import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.enums.RunStatus;
import com.tripagent.backend.entity.enums.TaskStatus;
import com.tripagent.backend.repository.EvalRunRepository;
import com.tripagent.backend.repository.EvalTaskRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalTaskStatusService {

  private final EvalTaskRepository evalTaskRepository;
  private final EvalRunRepository evalRunRepository;
  private final EntityManager entityManager;

  public EvalTaskStatusService(EvalTaskRepository evalTaskRepository, EvalRunRepository evalRunRepository,
                               EntityManager entityManager) {
    this.evalTaskRepository = evalTaskRepository;
    this.evalRunRepository = evalRunRepository;
    this.entityManager = entityManager;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refreshTaskStatus(Long taskId) {
    // 清除所有缓存，强制从数据库查询最新数据
    entityManager.clear();
    
    EvalTask task = evalTaskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("任务不存在: taskId=" + taskId));

    var latestRun = evalRunRepository.findTopByTaskTaskIdOrderByRunIdDesc(taskId).orElse(null);
    if (latestRun == null) {
      task.setStatus(TaskStatus.READY);
      evalTaskRepository.saveAndFlush(task);
      return;
    }

    if (latestRun.getStatus() == RunStatus.RUNNING) {
      task.setStatus(TaskStatus.RUNNING);
      evalTaskRepository.saveAndFlush(task);
      return;
    }

    if (latestRun.getStatus() == RunStatus.FAILED) {
      task.setStatus(TaskStatus.FAILED);
    } else {
      task.setStatus(TaskStatus.SUCCEEDED);
    }
    evalTaskRepository.saveAndFlush(task);
  }
}