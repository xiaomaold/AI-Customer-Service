package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.AgentTaskBuilder;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.chat.dto.ChatSendRequest;
import org.springframework.stereotype.Service;

@Service
public class AgentTaskBuilderImpl implements AgentTaskBuilder {

    @Override
    public AgentTask build(ChatSendRequest request, RecentConversationContext conversationContext) {
        TaskType taskType = resolveTaskType(request, conversationContext);
        return AgentTask.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .question(request.getQuestion())
                .selectedKnowledgeBaseId(request.getKnowledgeBaseId())
                .routeMode(request.getRouteMode())
                .routeReason(request.getRouteReason())
                .routeDomain(request.getRouteDomain())
                .routeIntent(request.getRouteIntent())
                .routeAction(request.getRouteAction())
                .conversationContext(conversationContext)
                .taskType(taskType)
                .requiresPlanning(taskType != TaskType.DIRECT_CHAT)
                .build();
    }

    private TaskType resolveTaskType(ChatSendRequest request, RecentConversationContext conversationContext) {
        if (StrUtil.equalsAnyIgnoreCase(request.getRouteReason(), "BUSINESS_CLARIFICATION", "ACTION_REQUEST")) {
            return TaskType.DIRECT_CHAT;
        }
        if (conversationContext != null && conversationContext.hasDocument()) {
            return TaskType.DOCUMENT_QA;
        }
        if (StrUtil.equalsAnyIgnoreCase(request.getRouteReason(), "DOCUMENT_DISCOVERY", "DOCUMENT_CARRYOVER")) {
            return TaskType.DOCUMENT_QA;
        }
        if (StrUtil.equalsIgnoreCase(request.getRouteReason(), "STRUCTURED_FACT_QUERY")) {
            return TaskType.STRUCTURED_FACT_QUERY;
        }
        if (StrUtil.equalsIgnoreCase(request.getRouteMode(), "HYBRID_GENERATION")) {
            return TaskType.HYBRID_SYNTHESIS;
        }
        if (StrUtil.equalsIgnoreCase(request.getRouteMode(), "UNIFIED_AGENT")) {
            return TaskType.KNOWLEDGE_QA;
        }
        return TaskType.DIRECT_CHAT;
    }
}
