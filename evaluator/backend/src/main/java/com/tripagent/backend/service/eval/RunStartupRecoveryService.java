package com.tripagent.backend.service.eval;

import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.enums.RunStatus;
import com.tripagent.backend.entity.enums.TaskStatus;
import com.tripagent.backend.repository.EvalRunRepository;
import com.tripagent.backend.repository.EvalTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RunStartupRecoveryService {

  private static final Logger log = LoggerFactory.getLogger(RunStartupRecoveryService.class);

  private final EvalRunRepository evalRunRepository;
  private final EvalTaskRepository evalTaskRepository;

  public RunStartupRecoveryService(
      EvalRunRepository evalRunRepository,
      EvalTaskRepository evalTaskRepository
  ) {
    this.evalRunRepository = evalRunRepository;
    this.evalTaskRepository = evalTaskRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void recoverStaleRuns() {
    List<EvalRun> staleRuns = evalRunRepository.findByStatus(RunStatus.RUNNING);
    if (staleRuns.isEmpty()) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    for (EvalRun run : staleRuns) {
      run.setStatus(RunStatus.FAILED);
      if (run.getEndTime() == null) {
        run.setEndTime(now);
      }
      evalRunRepository.save(run);

      EvalTask task = run.getTask();
      if (task != null && task.getStatus() == TaskStatus.RUNNING) {
        task.setStatus(TaskStatus.FAILED);
        evalTaskRepository.save(task);
      }

      Long taskId = task == null ? null : task.getTaskId();
      log.warn("Recovered stale eval run {} for task {} as FAILED", run.getRunId(), taskId);
    }
  }
}
