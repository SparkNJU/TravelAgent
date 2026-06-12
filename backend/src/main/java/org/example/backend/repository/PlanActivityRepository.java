package org.example.backend.repository;

import org.example.backend.entity.PlanActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanActivityRepository extends JpaRepository<PlanActivity, Long> {
    List<PlanActivity> findByPlanIdOrderByDayNumberAscActivityTimeAsc(Long planId);
    void deleteByPlanId(Long planId);
}
