package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalComparison;
import com.tripagent.backend.entity.enums.EvaluationDimension;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EvalComparisonRepository extends JpaRepository<EvalComparison, Long> {

  List<EvalComparison> findByRunRunIdOrderByComparisonIdAsc(Long runId);

  List<EvalComparison> findByRunRunIdAndDimension(Long runId, EvaluationDimension dimension);

  long countByRunRunId(Long runId);

  @Transactional
  void deleteByRunRunId(Long runId);
}
