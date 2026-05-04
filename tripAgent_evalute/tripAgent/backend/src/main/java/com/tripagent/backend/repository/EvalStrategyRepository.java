package com.tripagent.backend.repository;

import com.tripagent.backend.entity.EvalStrategy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalStrategyRepository extends JpaRepository<EvalStrategy, Long> {

  Optional<EvalStrategy> findByStrategyName(String strategyName);
}
