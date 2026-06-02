package org.example.backend.repository;

import org.example.backend.entity.AgentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentSkillRepository extends JpaRepository<AgentSkill, Long> {

    Optional<AgentSkill> findByName(String name);

    @Query("SELECT s FROM AgentSkill s WHERE s.isEnabled = true AND (s.userId IS NULL OR s.userId = :userId)")
    List<AgentSkill> findActiveSkillsForUser(@Param("userId") Long userId);

    @Query("SELECT s FROM AgentSkill s WHERE s.userId IS NULL OR s.userId = :userId")
    List<AgentSkill> findAllSkillsForUser(@Param("userId") Long userId);

    List<AgentSkill> findByIsEnabledTrueAndUserIdIsNull();
}
