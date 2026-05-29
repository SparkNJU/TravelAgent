package org.example.backend.repository;

import org.example.backend.entity.AgentMemoryChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentMemoryChangeLogRepository extends JpaRepository<AgentMemoryChangeLog, Long> {
    List<AgentMemoryChangeLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}