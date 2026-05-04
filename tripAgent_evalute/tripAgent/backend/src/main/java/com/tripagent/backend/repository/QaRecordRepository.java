package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalRun;
import com.tripagent.backend.entity.QaRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaRecordRepository extends JpaRepository<QaRecord, Long> {

  List<QaRecord> findByRun(EvalRun run);

  List<QaRecord> findByRunRunIdOrderByQaIdAsc(Long runId);
}
