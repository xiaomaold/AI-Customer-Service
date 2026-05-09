package com.airag.modules.agent.service;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.entity.ChatMessage;

import java.util.List;

public interface ConversationContextResolver {

    RecentConversationContext resolve(LoginUser loginUser,
                                      Long sessionId,
                                      Long selectedKnowledgeBaseId,
                                      List<ChatMessage> recentMessages,
                                      String currentQuestion,
                                      String carryoverKnowledgeBaseName,
                                      String carryoverDocumentName);
}
