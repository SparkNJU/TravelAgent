package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.enums.RunStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

  List<EvalRun> findByTask(EvalTask task);

  List<EvalRun> findByTaskTaskIdOrderByRunIdDesc(Long taskId);

  Page<EvalRun> findByTaskTaskId(Long taskId, Pageable pageable);

  Page<EvalRun> findByTaskTaskIdAndStatus(Long taskId, RunStatus status, Pageable pageable);

  List<EvalRun> findByStatus(RunStatus status);

  long countByTaskTaskIdAndStatus(Long taskId, RunStatus status);

  Optional<EvalRun> findTopByTaskTaskIdOrderByRunIdDesc(Long taskId);
}
