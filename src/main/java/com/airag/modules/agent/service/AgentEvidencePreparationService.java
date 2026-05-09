package com.airag.modules.agent.service;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.auth.security.LoginUser;

public interface AgentEvidencePreparationService {

    AgentEvidenceBundle prepare(LoginUser loginUser,
                                RecentConversationContext conversationContext,
                                Long requestedKnowledgeBaseId,
                                String routeMode,
                                String routeReason,
                                String routeDomain,
                                String routeIntent,
                                String executionProfile,
                                String executionDirective,
                                String question);
}
