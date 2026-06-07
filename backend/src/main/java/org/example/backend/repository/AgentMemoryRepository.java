package org.example.backend.repository;

import org.example.backend.entity.AgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {

    @Query("SELECT m FROM AgentMemory m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<AgentMemory> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM AgentMemory m WHERE m.isEnabled = true AND m.userId = :userId ORDER BY m.createdAt DESC")
    List<AgentMemory> findActiveByUserId(@Param("userId") Long userId);
}
