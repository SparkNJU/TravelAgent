package org.example.backend.repository;

import org.example.backend.entity.UserAgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAgentMemoryRepository extends JpaRepository<UserAgentMemory, Long> {
    Optional<UserAgentMemory> findByUserId(Long userId);

    List<UserAgentMemory> findByUserIdOrderByUpdatedAtDesc(Long userId);
}