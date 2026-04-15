package org.example.backend.repository;

import org.example.backend.entity.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {
    List<TravelPlan> findByUserId(Long userId);

    List<TravelPlan> findByUserIdAndStatus(Long userId, String status);

    Optional<TravelPlan> findByIdAndUserId(Long id, Long userId);
}
