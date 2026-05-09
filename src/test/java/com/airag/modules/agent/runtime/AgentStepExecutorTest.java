package com.airag.modules.agent.runtime;

import com.airag.modules.agent.context.RecentConversationContext;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentStepExecutorTest {

    @Test
    void shouldExecuteRegisteredToolStepsAndAppendDirective() {
        AgentTool tool = mock(AgentTool.class);
        AgentExecutionTraceService traceService = mock(AgentExecutionTraceService.class);
        AgentToolRegistry toolRegistry = new AgentToolRegistry(List.of(tool));
        AgentStepExecutor stepExecutor = new AgentStepExecutor(toolRegistry, traceService);

        TaskStep searchStep = TaskStep.builder()
                .stepOrder(1)
                .stepName("knowledge_lookup")
                .stepType(TaskStepType.SEARCH_KNOWLEDGE)
                .instruction("Search knowledge")
                .toolName("knowledgeSearch")
                .outputKey("knowledgeEvidence")
                .required(true)
                .build();
        TaskStep answerStep = TaskStep.builder()
                .stepOrder(2)
                .stepName("knowledge_answer")
                .stepType(TaskStepType.GENERATE_ANSWER)
                .instruction("Generate answer")
                .toolName("llmGeneration")
                .outputKey("draftAnswer")
                .required(true)
                .build();
        TaskPlan plan = TaskPlan.builder()
                .taskType(TaskType.KNOWLEDGE_QA)
                .plannerReason("KNOWLEDGE_QA_PLAN")
                .steps(List.of(searchStep, answerStep))
                .finalAnswerDirectly(false)
                .build();
        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .question("refund process")
                .conversationContext(RecentConversationContext.builder().knowledgeBaseName("after-sales-kb").build())
                .taskType(TaskType.KNOWLEDGE_QA)
                .build();
        ChatSendRequest request = new ChatSendRequest();
        request.setExecutionTraceId("trace-1");
        request.setExecutionDirective("Original directive");
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();

        when(tool.supports(eq(searchStep))).thenReturn(true);
        when(tool.execute(any(), eq(searchStep))).thenReturn(
                AgentToolExecutionResult.builder()
                        .outputValue("RESULT_TYPE: KNOWLEDGE_SEARCH\nMATCHED_COUNT: 1")
                        .outputSummary("Prepared knowledge evidence")
                        .resultStatus(AgentResultStatus.COMPLETED)
                        .build()
        );

        AgentStepExecutionResult result = stepExecutor.execute(task, plan, request, loginUser);

        assertEquals("RESULT_TYPE: KNOWLEDGE_SEARCH\nMATCHED_COUNT: 1", result.stepOutputs().get("knowledgeEvidence"));
        assertTrue(request.getExecutionDirective().contains("Original directive"));
        assertTrue(request.getExecutionDirective().contains("[Pre-executed step result]"));
        assertTrue(request.getExecutionDirective().contains("knowledge_lookup"));
        assertTrue(request.getExecutionDirective().contains("RESULT_TYPE: KNOWLEDGE_SEARCH"));

        ArgumentCaptor<AgentExecutionTrace> traceCaptor = ArgumentCaptor.forClass(AgentExecutionTrace.class);
        verify(traceService, org.mockito.Mockito.times(4)).trace(traceCaptor.capture());
        List<AgentExecutionTrace> traces = traceCaptor.getAllValues();
        assertTrue(traces.stream().anyMatch(trace -> "STEP_STARTED".equals(trace.stage()) && "knowledge_lookup".equals(trace.stepName())));
        assertTrue(traces.stream().anyMatch(trace -> "STEP_COMPLETED".equals(trace.stage()) && AgentResultStatus.COMPLETED == trace.resultStatus()));
        assertTrue(traces.stream().anyMatch(trace -> "STEP_COMPLETED".equals(trace.stage()) && AgentResultStatus.SKIPPED == trace.resultStatus()));
        assertTrue(traces.stream().anyMatch(trace -> "STEP_COMPLETED".equals(trace.stage()) && "STEP_DEFERRED".equals(trace.status())));
    }
}
