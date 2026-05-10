package com.tripagent.backend.repository;

import com.tripagent.backend.entity.MetricSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

  Optional<MetricSnapshot> findByRunRunId(Long runId);

  @Transactional
  void deleteByRunRunId(Long runId);
}
