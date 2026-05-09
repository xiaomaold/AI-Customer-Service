package com.airag.modules.agent.executor.impl;

import com.airag.modules.agent.runtime.AgentStepExecutor;
import com.airag.modules.agent.strategy.AgentExecutionStrategy;
import com.airag.modules.agent.strategy.StrategyExecutionResult;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskStep;
import com.airag.modules.agent.task.TaskStepType;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import com.airag.modules.agent.trace.AgentResultStatus;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskExecutorImplTest {

    @Test
    void shouldUseFirstMatchingStrategy() {
        AgentExecutionStrategy generalStrategy = mock(AgentExecutionStrategy.class);
        AgentExecutionStrategy agentStrategy = mock(AgentExecutionStrategy.class);
        AgentStepExecutor stepExecutor = mock(AgentStepExecutor.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentTaskExecutorImpl executor = new AgentTaskExecutorImpl(List.of(generalStrategy, agentStrategy), stepExecutor, traceService);

        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .taskType(TaskType.DIRECT_CHAT)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.DIRECT_CHAT)
                .plannerReason("DIRECT_CHAT")
                .steps(List.of())
                .finalAnswerDirectly(true)
                .build();
        ChatSendRequest request = new ChatSendRequest();
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();
        SseEmitter emitter = new SseEmitter();

        when(generalStrategy.supports(eq(task), eq(plan))).thenReturn(true);
        when(generalStrategy.strategyName()).thenReturn("GENERAL_GENERATION");
        when(generalStrategy.execute(eq(task), eq(plan), eq(request), eq(loginUser))).thenReturn(
                StrategyExecutionResult.builder()
                        .emitter(emitter)
                        .resultStatus(AgentResultStatus.STREAM_STARTED)
                        .outputSummary("Delegated to general generation")
                        .build()
        );

        SseEmitter result = executor.execute(task, plan, request, loginUser);

        assertSame(emitter, result);
        verify(stepExecutor).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(generalStrategy).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(agentStrategy, never()).execute(any(), any(), any(), any());
    }

    @Test
    void shouldTraceStrategyLifecycleAfterStepExecution() {
        AgentExecutionStrategy strategy = mock(AgentExecutionStrategy.class);
        AgentStepExecutor stepExecutor = mock(AgentStepExecutor.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentTaskExecutorImpl executor = new AgentTaskExecutorImpl(List.of(strategy), stepExecutor, traceService);

        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .taskType(TaskType.DOCUMENT_QA)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.DOCUMENT_QA)
                .plannerReason("DOCUMENT_QA_PLAN")
                .steps(List.of(
                        TaskStep.builder()
                                .stepOrder(1)
                                .stepName("document_evidence")
                                .stepType(TaskStepType.SEARCH_DOCUMENT)
                                .instruction("Search document evidence")
                                .toolName("knowledgeDocumentSearch")
                                .outputKey("documentEvidence")
                                .required(true)
                                .build(),
                        TaskStep.builder()
                                .stepOrder(2)
                                .stepName("document_answer")
                                .stepType(TaskStepType.GENERATE_ANSWER)
                                .instruction("Generate answer")
                                .toolName("llmGeneration")
                                .outputKey("draftAnswer")
                                .required(true)
                                .build()
                ))
                .finalAnswerDirectly(false)
                .build();
        ChatSendRequest request = new ChatSendRequest();
        request.setExecutionTraceId("trace-1");
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();
        SseEmitter emitter = new SseEmitter();

        when(strategy.supports(eq(task), eq(plan))).thenReturn(true);
        when(strategy.strategyName()).thenReturn("DOCUMENT_QA");
        when(strategy.execute(eq(task), eq(plan), eq(request), eq(loginUser))).thenReturn(
                StrategyExecutionResult.builder()
                        .emitter(emitter)
                        .resultStatus(AgentResultStatus.STREAM_STARTED)
                        .outputSummary("Delegated to document QA with carryover")
                        .build()
        );

        executor.execute(task, plan, request, loginUser);

        ArgumentCaptor<AgentExecutionTrace> traceCaptor = ArgumentCaptor.forClass(AgentExecutionTrace.class);
        verify(stepExecutor).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(traceService, org.mockito.Mockito.times(2)).trace(traceCaptor.capture());
        List<AgentExecutionTrace> traces = traceCaptor.getAllValues();
        assertTrue(traces.stream().anyMatch(trace -> "STRATEGY_SELECTED".equals(trace.stage())));
        assertTrue(traces.stream().anyMatch(trace -> "STRATEGY_EXECUTED".equals(trace.stage())));
        assertTrue(traces.stream().anyMatch(trace -> "STRATEGY_EXECUTED".equals(trace.stage()) && AgentResultStatus.STREAM_STARTED == trace.resultStatus()));
        assertTrue(traces.stream().anyMatch(trace -> "STRATEGY_EXECUTED".equals(trace.stage()) && "Delegated to document QA with carryover".equals(trace.outputSummary())));
    }

    @Test
    void shouldThrowWhenNoStrategyMatches() {
        AgentExecutionStrategy strategy = mock(AgentExecutionStrategy.class);
        AgentStepExecutor stepExecutor = mock(AgentStepExecutor.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentTaskExecutorImpl executor = new AgentTaskExecutorImpl(List.of(strategy), stepExecutor, traceService);

        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .taskType(TaskType.KNOWLEDGE_QA)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.KNOWLEDGE_QA)
                .plannerReason("ROUTE_DRIVEN")
                .steps(List.of())
                .finalAnswerDirectly(false)
                .build();

        when(strategy.supports(eq(task), eq(plan))).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> executor.execute(task, plan, new ChatSendRequest(), LoginUser.builder().userId(1001L).build()));
    }

    @Test
    void shouldPreferDocumentQaStrategyForDocumentTask() {
        AgentExecutionStrategy documentStrategy = mock(AgentExecutionStrategy.class);
        AgentExecutionStrategy unifiedStrategy = mock(AgentExecutionStrategy.class);
        AgentStepExecutor stepExecutor = mock(AgentStepExecutor.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentTaskExecutorImpl executor = new AgentTaskExecutorImpl(List.of(documentStrategy, unifiedStrategy), stepExecutor, traceService);

        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .taskType(TaskType.DOCUMENT_QA)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.DOCUMENT_QA)
                .plannerReason("DOCUMENT_CARRYOVER")
                .steps(List.of())
                .finalAnswerDirectly(false)
                .build();
        ChatSendRequest request = new ChatSendRequest();
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();
        SseEmitter emitter = new SseEmitter();

        when(documentStrategy.supports(eq(task), eq(plan))).thenReturn(true);
        when(documentStrategy.strategyName()).thenReturn("DOCUMENT_QA");
        when(documentStrategy.execute(eq(task), eq(plan), eq(request), eq(loginUser))).thenReturn(
                StrategyExecutionResult.builder()
                        .emitter(emitter)
                        .resultStatus(AgentResultStatus.STREAM_STARTED)
                        .outputSummary("Delegated to document QA")
                        .build()
        );

        SseEmitter result = executor.execute(task, plan, request, loginUser);

        assertSame(emitter, result);
        verify(stepExecutor).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(documentStrategy).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(unifiedStrategy, never()).execute(any(), any(), any(), any());
    }

    @Test
    void shouldPreferStructuredFactStrategyForStructuredFactTask() {
        AgentExecutionStrategy structuredFactStrategy = mock(AgentExecutionStrategy.class);
        AgentExecutionStrategy unifiedStrategy = mock(AgentExecutionStrategy.class);
        AgentStepExecutor stepExecutor = mock(AgentStepExecutor.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentTaskExecutorImpl executor = new AgentTaskExecutorImpl(List.of(structuredFactStrategy, unifiedStrategy), stepExecutor, traceService);

        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .taskType(TaskType.STRUCTURED_FACT_QUERY)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.STRUCTURED_FACT_QUERY)
                .plannerReason("STRUCTURED_FACT_QUERY")
                .steps(List.of())
                .finalAnswerDirectly(false)
                .build();
        ChatSendRequest request = new ChatSendRequest();
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();
        SseEmitter emitter = new SseEmitter();

        when(structuredFactStrategy.supports(eq(task), eq(plan))).thenReturn(true);
        when(structuredFactStrategy.strategyName()).thenReturn("STRUCTURED_FACT");
        when(structuredFactStrategy.execute(eq(task), eq(plan), eq(request), eq(loginUser))).thenReturn(
                StrategyExecutionResult.builder()
                        .emitter(emitter)
                        .resultStatus(AgentResultStatus.STREAM_STARTED)
                        .outputSummary("Delegated to structured fact")
                        .build()
        );

        SseEmitter result = executor.execute(task, plan, request, loginUser);

        assertSame(emitter, result);
        verify(stepExecutor).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(structuredFactStrategy).execute(eq(task), eq(plan), eq(request), eq(loginUser));
        verify(unifiedStrategy, never()).execute(any(), any(), any(), any());
    }
}
