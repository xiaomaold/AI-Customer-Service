package com.airag.modules.agent.strategy.impl;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.AgentChatService;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentQaExecutionStrategyTest {

    private final AgentChatService agentChatService = mock(AgentChatService.class);
    private final DocumentQaExecutionStrategy strategy = new DocumentQaExecutionStrategy(agentChatService);

    @Test
    void shouldSupportOnlyDocumentQaTask() {
        assertTrue(strategy.supports(
                AgentTask.builder().taskType(TaskType.DOCUMENT_QA).build(),
                TaskPlan.builder().taskType(TaskType.DOCUMENT_QA).build()
        ));
        assertFalse(strategy.supports(
                AgentTask.builder().taskType(TaskType.KNOWLEDGE_QA).build(),
                TaskPlan.builder().taskType(TaskType.KNOWLEDGE_QA).build()
        ));
    }

    @Test
    void shouldSetExecutionProfileBeforeDelegating() {
        ChatSendRequest request = new ChatSendRequest();
        SseEmitter emitter = new SseEmitter();
        when(agentChatService.streamChat(request)).thenReturn(emitter);

        strategy.execute(
                AgentTask.builder()
                        .taskType(TaskType.DOCUMENT_QA)
                        .conversationContext(RecentConversationContext.builder()
                                .knowledgeBaseName("lab-kb")
                                .documentName("vision-report")
                                .build())
                        .build(),
                TaskPlan.builder().taskType(TaskType.DOCUMENT_QA).build(),
                request,
                LoginUser.builder().userId(1001L).build()
        );

        assertEquals("DOCUMENT_QA", request.getExecutionProfile());
        assertEquals("vision-report", request.getCarryoverDocumentName());
        assertEquals("lab-kb", request.getCarryoverKnowledgeBaseName());
        assertTrue(request.getExecutionDirective().contains("active document"));
    }
}
