package com.airag.modules.agent.tools;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.KnowledgeAgentFacade;
import com.airag.modules.auth.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeAgentToolsFactory {

    private final KnowledgeAgentFacade knowledgeAgentFacade;

    public KnowledgeAgentTools create(LoginUser loginUser, RecentConversationContext context) {
        return new KnowledgeAgentTools(knowledgeAgentFacade, loginUser, context);
    }
}
