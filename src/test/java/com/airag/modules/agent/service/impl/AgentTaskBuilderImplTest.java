package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.chat.dto.ChatSendRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskBuilderImplTest {

    private final AgentTaskBuilderImpl builder = new AgentTaskBuilderImpl();

    @Test
    void shouldBuildDocumentTaskFromConversationContext() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("主要内容是什么");
        request.setRouteMode("UNIFIED_AGENT");
        request.setRouteReason("DOCUMENT_CARRYOVER");

        RecentConversationContext context = RecentConversationContext.builder()
                .documentName("图像的几何运算实验报告")
                .knowledgeBaseName("实验报告库")
                .applyDocumentCarryover(true)
                .applyKnowledgeBaseCarryover(true)
                .build();

        AgentTask task = builder.build(request, context);

        assertEquals(TaskType.DOCUMENT_QA, task.taskType());
        assertTrue(task.requiresPlanning());
        assertEquals("图像的几何运算实验报告", task.conversationContext().getDocumentName());
    }

    @Test
    void shouldBuildDirectChatTaskForGeneralRoute() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("苹果怎么吃");
        request.setRouteMode("GENERAL_GENERATION");
        request.setRouteReason("GENERAL_REQUEST");

        AgentTask task = builder.build(request, RecentConversationContext.empty());

        assertEquals(TaskType.DIRECT_CHAT, task.taskType());
    }
    @Test
    void shouldPreferBusinessClarificationOverDocumentConversationContext() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("退款");
        request.setRouteMode("GENERAL_GENERATION");
        request.setRouteReason("BUSINESS_CLARIFICATION");

        RecentConversationContext context = RecentConversationContext.builder()
                .documentName("02-退款与售后政策")
                .knowledgeBaseName("企业知识库")
                .explicitDocumentInQuestion(true)
                .applyDocumentCarryover(true)
                .build();

        AgentTask task = builder.build(request, context);

        assertEquals(TaskType.DIRECT_CHAT, task.taskType());
    }

    @Test
    void shouldPreferActionRequestOverDocumentConversationContext() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("我要退款");
        request.setRouteMode("GENERAL_GENERATION");
        request.setRouteReason("ACTION_REQUEST");

        RecentConversationContext context = RecentConversationContext.builder()
                .documentName("02-退款与售后政策")
                .knowledgeBaseName("企业知识库")
                .explicitDocumentInQuestion(true)
                .applyDocumentCarryover(true)
                .build();

        AgentTask task = builder.build(request, context);

        assertEquals(TaskType.DIRECT_CHAT, task.taskType());
    }
}
