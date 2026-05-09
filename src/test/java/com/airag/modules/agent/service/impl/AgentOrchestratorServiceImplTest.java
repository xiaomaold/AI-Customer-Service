package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.executor.AgentTaskExecutor;
import com.airag.modules.agent.planner.AgentTaskPlanner;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.service.AgentTaskBuilder;
import com.airag.modules.agent.service.ConversationContextResolver;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskStep;
import com.airag.modules.agent.task.TaskStepType;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.service.ChatMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorServiceImplTest {

    @Test
    void shouldTracePlanBeforeExecutingTask() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        ConversationContextResolver conversationContextResolver = mock(ConversationContextResolver.class);
        AgentTaskBuilder agentTaskBuilder = mock(AgentTaskBuilder.class);
        AgentTaskPlanner agentTaskPlanner = mock(AgentTaskPlanner.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentTaskExecutor agentTaskExecutor = mock(AgentTaskExecutor.class);
        AgentOrchestratorServiceImpl service = new AgentOrchestratorServiceImpl(
                chatMessageService,
                conversationContextResolver,
                agentTaskBuilder,
                agentTaskPlanner,
                traceService,
                agentTaskExecutor
        );
        ReflectionTestUtils.setField(service, "historyLimit", 6);

        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("summarize");
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();
        RecentConversationContext context = RecentConversationContext.empty();
        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .taskType(TaskType.DOCUMENT_QA)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.DOCUMENT_QA)
                .plannerReason("DOCUMENT_CARRYOVER")
                .steps(List.of(TaskStep.builder()
                        .stepOrder(1)
                        .stepName("document_evidence")
                        .stepType(TaskStepType.SEARCH_DOCUMENT)
                        .instruction("Search document evidence")
                        .toolName("knowledgeDocumentSearch")
                        .outputKey("documentEvidence")
                        .required(true)
                        .build()))
                .finalAnswerDirectly(false)
                .build();
        SseEmitter emitter = new SseEmitter();

        when(chatMessageService.listRecentMessages(1L, 6)).thenReturn(List.of());
        when(conversationContextResolver.resolve(eq(loginUser), eq(1L), eq(null), eq(List.of()), eq("summarize"), eq(null), eq(null)))
                .thenReturn(context);
        when(agentTaskBuilder.build(eq(request), eq(context))).thenReturn(task);
        when(agentTaskPlanner.plan(eq(task))).thenReturn(plan);
        when(agentTaskExecutor.execute(eq(task), eq(plan), eq(request), eq(loginUser))).thenReturn(emitter);

        service.streamChat(request, loginUser);

        verify(traceService, atLeast(2)).trace(any());
        verify(agentTaskExecutor).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        org.junit.jupiter.api.Assertions.assertNotNull(request.getExecutionTraceId());
    }
}
