package com.airag.modules.agent.task;

import com.airag.modules.agent.context.RecentConversationContext;
import lombok.Builder;

@Builder
public record AgentTask(Long sessionId,
                        Long userId,
                        String question,
                        Long selectedKnowledgeBaseId,
                        String routeMode,
                        String routeReason,
                        String routeDomain,
                        String routeIntent,
                        String routeAction,
                        RecentConversationContext conversationContext,
                        TaskType taskType,
                        boolean requiresPlanning) {
}
