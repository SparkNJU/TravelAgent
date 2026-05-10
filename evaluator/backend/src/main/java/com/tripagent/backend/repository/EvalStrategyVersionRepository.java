package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalStrategyVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalStrategyVersionRepository extends JpaRepository<EvalStrategyVersion, Long> {

  List<EvalStrategyVersion> findByStrategyStrategyIdOrderByVersionDesc(Long strategyId);

  Optional<EvalStrategyVersion> findByStrategyStrategyIdAndVersion(Long strategyId, Integer version);
}
