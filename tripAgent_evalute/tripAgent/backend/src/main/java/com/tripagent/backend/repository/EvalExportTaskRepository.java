package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalExportTask;
import com.tripagent.backend.entity.enums.ExportTaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalExportTaskRepository extends JpaRepository<EvalExportTask, Long> {

  Page<EvalExportTask> findByTaskId(Long taskId, Pageable pageable);

  Page<EvalExportTask> findByTaskIdAndStatus(Long taskId, ExportTaskStatus status, Pageable pageable);

  Page<EvalExportTask> findByStatus(ExportTaskStatus status, Pageable pageable);

  List<EvalExportTask> findByStatusInAndCompletedAtBefore(List<ExportTaskStatus> statuses, LocalDateTime completedAt);

  List<EvalExportTask> findByCreatedAtAfter(LocalDateTime createdAt);

  List<EvalExportTask> findByStatusAndCreatedAtBefore(ExportTaskStatus status, LocalDateTime createdAt);
}
