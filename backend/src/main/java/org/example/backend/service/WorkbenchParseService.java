package org.example.backend.service;

import org.example.backend.entity.ChatConversation;
import org.example.backend.dto.TravelPlanResponse;
import org.example.backend.repository.ChatConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkbenchParseService {

    private static final Logger logger = LoggerFactory.getLogger(WorkbenchParseService.class);

    @Autowired
    private TravelPlanService travelPlanService;

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Async
    @Transactional
    public void parseAsync(Long conversationId) {
        logger.info("Starting async workbench parsing for conversation {}", conversationId);
        try {
            TravelPlanResponse plan = travelPlanService.parseAndSaveConversation(conversationId);
            ChatConversation conv = chatConversationRepository.findById(conversationId).orElse(null);
            if (conv != null) {
                conv.setWorkbenchPlanId(plan.getPlanId());
                conv.setWorkbenchStatus("done");
                conv.setWorkbenchError(null);
                chatConversationRepository.save(conv);
                logger.info("Workbench parsing done for conversation {}: planId={}", conversationId, plan.getPlanId());
            }
        } catch (Exception e) {
            logger.error("Workbench parsing failed for conversation {}: {}", conversationId, e.getMessage());
            try {
                ChatConversation conv = chatConversationRepository.findById(conversationId).orElse(null);
                if (conv != null) {
                    conv.setWorkbenchStatus("failed");
                    conv.setWorkbenchError(e.getMessage());
                    chatConversationRepository.save(conv);
                }
            } catch (Exception ex) {
                logger.error("Failed to update workbench error status for conversation {}", conversationId, ex);
            }
        }
    }
}
