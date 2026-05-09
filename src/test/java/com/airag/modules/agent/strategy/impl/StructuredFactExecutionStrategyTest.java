package com.airag.modules.agent.strategy.impl;

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

class StructuredFactExecutionStrategyTest {

    private final AgentChatService agentChatService = mock(AgentChatService.class);
    private final StructuredFactExecutionStrategy strategy = new StructuredFactExecutionStrategy(agentChatService);

    @Test
    void shouldSupportOnlyStructuredFactTask() {
        assertTrue(strategy.supports(
                AgentTask.builder().taskType(TaskType.STRUCTURED_FACT_QUERY).build(),
                TaskPlan.builder().taskType(TaskType.STRUCTURED_FACT_QUERY).build()
        ));
        assertFalse(strategy.supports(
                AgentTask.builder().taskType(TaskType.HYBRID_SYNTHESIS).build(),
                TaskPlan.builder().taskType(TaskType.HYBRID_SYNTHESIS).build()
        ));
    }

    @Test
    void shouldSetExecutionProfileBeforeDelegating() {
        ChatSendRequest request = new ChatSendRequest();
        SseEmitter emitter = new SseEmitter();
        when(agentChatService.streamChat(request)).thenReturn(emitter);

        strategy.execute(
                AgentTask.builder().taskType(TaskType.STRUCTURED_FACT_QUERY).build(),
                TaskPlan.builder().taskType(TaskType.STRUCTURED_FACT_QUERY).build(),
                request,
                LoginUser.builder().userId(1001L).build()
        );

        assertEquals("STRUCTURED_FACT", request.getExecutionProfile());
        assertTrue(request.getExecutionDirective().contains("structured facts"));
    }
}
