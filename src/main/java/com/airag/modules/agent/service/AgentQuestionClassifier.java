package com.airag.modules.agent.service;

import com.airag.modules.agent.context.RecentConversationContext;

public interface AgentQuestionClassifier {

    boolean isKnowledgeBaseListQuestion(String question);

    String resolveKnowledgeBaseKeyword(String question, RecentConversationContext conversationContext);

    String resolveDocumentKeyword(String question, RecentConversationContext conversationContext);

    boolean isStructuredFactQuestion(String question);
}
