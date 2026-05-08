// src/main/java/org/example/backend/repository/LikeRecordRepository.java
package org.example.backend.repository;

import org.example.backend.entity.LikeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRecordRepository extends JpaRepository<LikeRecord, Long> {
    Optional<LikeRecord> findByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);
}