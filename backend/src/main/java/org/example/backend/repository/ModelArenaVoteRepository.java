package org.example.backend.repository;

import org.example.backend.entity.ModelArenaVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelArenaVoteRepository extends JpaRepository<ModelArenaVote, Long> {
}
