package org.example.backend.repository;

import org.example.backend.entity.AgentPublicKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentPublicKnowledgeRepository extends JpaRepository<AgentPublicKnowledge, Long> {
    Optional<AgentPublicKnowledge> findByKnowledgeKey(String knowledgeKey);

    List<AgentPublicKnowledge> findByKnowledgeScopeOrderByUpdatedAtDesc(String knowledgeScope);
}