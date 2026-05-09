package com.airag.modules.agent.planner.impl;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskPlannerImplTest {

    private final AgentTaskPlannerImpl planner = new AgentTaskPlannerImpl();

    @Test
    void shouldPlanDocumentQaAsMultiStepFlow() {
        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .question("主要内容是什么")
                .routeReason("DOCUMENT_CARRYOVER")
                .conversationContext(RecentConversationContext.builder()
                        .documentName("图像的几何运算实验报告")
                        .build())
                .taskType(TaskType.DOCUMENT_QA)
                .requiresPlanning(true)
                .build();

        TaskPlan plan = planner.plan(task);

        assertEquals(TaskType.DOCUMENT_QA, plan.taskType());
        assertEquals("DOCUMENT_CARRYOVER", plan.plannerReason());
        assertEquals(3, plan.steps().size());
        assertFalse(plan.finalAnswerDirectly());
        assertEquals("document_evidence", plan.steps().get(0).stepName());
        assertEquals("documentEvidence", plan.steps().get(0).outputKey());
        assertEquals("draftAnswer", plan.steps().get(1).outputKey());
    }

    @Test
    void shouldPlanDirectChatAsDirectAnswer() {
        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .question("你好")
                .taskType(TaskType.DIRECT_CHAT)
                .requiresPlanning(false)
                .build();

        TaskPlan plan = planner.plan(task);

        assertEquals(TaskType.DIRECT_CHAT, plan.taskType());
        assertTrue(plan.finalAnswerDirectly());
        assertEquals(1, plan.steps().size());
        assertEquals("direct_answer", plan.steps().get(0).stepName());
        assertEquals("finalAnswer", plan.steps().get(0).outputKey());
    }

    @Test
    void shouldPlanStructuredFactAsFieldOrientedFlow() {
        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .question("公司电话是多少")
                .taskType(TaskType.STRUCTURED_FACT_QUERY)
                .requiresPlanning(true)
                .build();

        TaskPlan plan = planner.plan(task);

        assertEquals("STRUCTURED_FACT_PLAN", plan.plannerReason());
        assertEquals(3, plan.steps().size());
        assertEquals("structured_fact_lookup", plan.steps().get(0).stepName());
        assertEquals("structuredFactEvidence", plan.steps().get(0).outputKey());
    }

    @Test
    void shouldPlanHybridSynthesisWithOptionalDocumentStrengthening() {
        AgentTask task = AgentTask.builder()
                .sessionId(1L)
                .userId(1001L)
                .question("根据制度帮我写一份请假说明")
                .taskType(TaskType.HYBRID_SYNTHESIS)
                .requiresPlanning(true)
                .build();

        TaskPlan plan = planner.plan(task);

        assertEquals("HYBRID_SYNTHESIS_PLAN", plan.plannerReason());
        assertEquals(4, plan.steps().size());
        assertFalse(plan.steps().get(1).required());
        assertEquals("documentEvidence", plan.steps().get(1).outputKey());
        assertEquals("validatedAnswer", plan.steps().get(3).outputKey());
    }
}
