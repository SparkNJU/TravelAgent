package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalExportAudit;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalExportAuditRepository extends JpaRepository<EvalExportAudit, Long> {

  Page<EvalExportAudit> findByExportIdOrderByAuditIdDesc(Long exportId, Pageable pageable);

  List<EvalExportAudit> findByCreatedAtAfter(LocalDateTime createdAt);

  long countByCreatedAtAfterAndAction(LocalDateTime createdAt, String action);
}
