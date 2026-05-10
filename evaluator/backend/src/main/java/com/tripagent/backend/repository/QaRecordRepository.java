package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.QaRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface QaRecordRepository extends JpaRepository<QaRecord, Long> {

  List<QaRecord> findByRun(EvalRun run);

  List<QaRecord> findByRunRunIdOrderByQaIdAsc(Long runId);

  @Transactional
  void deleteByRunRunId(Long runId);
}
