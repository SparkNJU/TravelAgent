package org.example.backend.service;

import jakarta.annotation.PostConstruct;
import org.example.backend.entity.AgentMemory;
import org.example.backend.repository.AgentMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);

    @Autowired
    private AgentMemoryRepository memoryRepository;

    @PostConstruct
    public void seedMemories() {
        try {
            log.info("Checking and seeding default user memories...");
            // Seed for standard user with ID 1
            Long userId = 1L;
            List<AgentMemory> existing = memoryRepository.findAllByUserId(userId);
            if (existing.isEmpty()) {
                memoryRepository.save(new AgentMemory("偏爱深度游和慢节奏，每天规划的景点不要超过3个", true, userId));
                memoryRepository.save(new AgentMemory("对海鲜过敏，在推荐美食时请避免推荐海鲜餐馆", true, userId));
                memoryRepository.save(new AgentMemory("出行预算偏向经济型，住宿优先考虑舒适型民宿或3星级酒店", true, userId));
                log.info("Seeded default memories for user 1");
            }
        } catch (Exception e) {
            log.error("Failed to seed default user memories", e);
        }
    }

    public List<AgentMemory> getAllMemoriesForUser(Long userId) {
        return memoryRepository.findAllByUserId(userId);
    }

    public List<AgentMemory> getActiveMemoriesForUser(Long userId) {
        return memoryRepository.findActiveByUserId(userId);
    }

    public Optional<AgentMemory> getMemoryById(Long id) {
        return memoryRepository.findById(id);
    }

    public AgentMemory saveMemory(AgentMemory memory, Long userId) {
        memory.setUserId(userId);
        return memoryRepository.save(memory);
    }

    public AgentMemory updateMemory(Long id, AgentMemory updatedData, Long userId) {
        AgentMemory existing = memoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该记忆不存在"));

        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改他人的个性化记忆");
        }

        if (updatedData.getContent() != null) {
            existing.setContent(updatedData.getContent());
        }
        if (updatedData.getIsEnabled() != null) {
            existing.setIsEnabled(updatedData.getIsEnabled());
        }

        return memoryRepository.save(existing);
    }

    public void deleteMemory(Long id, Long userId) {
        AgentMemory existing = memoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该记忆不存在"));

        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人的个性化记忆");
        }

        memoryRepository.delete(existing);
    }

    public AgentMemory toggleMemoryStatus(Long id, Boolean isEnabled, Long userId) {
        AgentMemory existing = memoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该记忆不存在"));

        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改他人的个性化记忆状态");
        }

        existing.setIsEnabled(isEnabled);
        return memoryRepository.save(existing);
    }
}
