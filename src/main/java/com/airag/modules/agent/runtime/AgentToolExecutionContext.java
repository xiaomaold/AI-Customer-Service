package com.airag.modules.agent.runtime;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;

import java.util.Map;

public record AgentToolExecutionContext(AgentTask task,
                                        TaskPlan plan,
                                        ChatSendRequest request,
                                        LoginUser loginUser,
                                        Map<String, String> stepOutputs) {

    public String question() {
        return task == null ? null : task.question();
    }

    public String knowledgeBaseKeyword() {
        RecentConversationContext context = conversationContext();
        return context == null ? null : context.getKnowledgeBaseName();
    }

    public String documentKeyword() {
        RecentConversationContext context = conversationContext();
        return context == null ? null : context.getDocumentName();
    }

    public RecentConversationContext conversationContext() {
        return task == null ? null : task.conversationContext();
    }
}
