package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalTask;
import com.tripagent.backend.entity.enums.TaskStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalTaskRepository extends JpaRepository<EvalTask, Long> {

  List<EvalTask> findByStatus(TaskStatus status);

  List<EvalTask> findByAgentVersion(String agentVersion);

  List<EvalTask> findByStatusAndAgentVersion(TaskStatus status, String agentVersion);
}
