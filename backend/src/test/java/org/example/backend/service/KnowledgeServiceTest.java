package org.example.backend.service;

import org.example.backend.dto.KnowledgeSyncTurnRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeServiceTest {

    @Test
    void buildKnowledgeMarkdownIncludesConversationAnswerAndWebSearchResults() {
        KnowledgeSyncTurnRequest request = new KnowledgeSyncTurnRequest();
        request.setTitle("东京5日游");
        request.setConversationId("local-1");
        request.setTurnIndex(2);
        request.setUserMessage("帮我做东京5天计划");
        request.setPlanContent("1. 搜索东京景点\n2. 搜索东京美食");
        request.setAssistantAnswer("# 东京5日游\n浅草寺、银座、美食路线");
        request.setWebSearchResults(List.of(
                new KnowledgeSyncTurnRequest.WebSearchGroup(
                        "东京 五天 美食",
                        List.of(Map.of(
                                "title", "东京美食推荐",
                                "link", "https://example.com/tokyo-food",
                                "snippet", "寿司、拉面、居酒屋"
                        ))
                )
        ));

        KnowledgeService service = new KnowledgeService();
        String markdown = service.buildKnowledgeMarkdown(request);

        assertTrue(markdown.contains("# 东京5日游"));
        assertTrue(markdown.contains("## 用户问题\n帮我做东京5天计划"));
        assertTrue(markdown.contains("## Agent 执行计划\n1. 搜索东京景点"));
        assertTrue(markdown.contains("### 搜索：东京 五天 美食"));
        assertTrue(markdown.contains("[东京美食推荐](https://example.com/tokyo-food)"));
        assertTrue(markdown.contains("## 最终回答\n# 东京5日游"));
        assertTrue(markdown.contains("conversation_id: local-1"));
        assertTrue(markdown.contains("turn_index: 2"));
    }
}
