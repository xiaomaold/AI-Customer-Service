package com.airag.modules.agent.service;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.chat.dto.ChatSendRequest;

public interface AgentTaskBuilder {

    AgentTask build(ChatSendRequest request, RecentConversationContext conversationContext);
}
