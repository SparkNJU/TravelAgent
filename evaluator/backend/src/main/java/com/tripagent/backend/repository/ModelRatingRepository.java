package com.tripagent.backend.repository;

import com.tripagent.backend.entity.ModelRating;
import com.tripagent.backend.entity.enums.EvaluationDimension;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ModelRatingRepository extends JpaRepository<ModelRating, Long> {

  List<ModelRating> findByRunRunId(Long runId);

  List<ModelRating> findByRunRunIdAndDimension(Long runId, EvaluationDimension dimension);

  @Transactional
  void deleteByRunRunId(Long runId);
}
