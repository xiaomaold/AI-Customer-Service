package com.airag.modules.agent.service;

import com.airag.modules.auth.security.LoginUser;

public interface KnowledgeAgentFacade {

    String queryKnowledgeBases(LoginUser loginUser, String keyword, Integer limit);

    String queryKnowledgeDocuments(LoginUser loginUser,
                                   String knowledgeBaseKeyword,
                                   String documentKeyword,
                                   Integer limit,
                                   boolean includeDocumentNames);

    String searchKnowledge(LoginUser loginUser, String question, String knowledgeBaseKeyword, Integer topK);

    String searchDocumentContent(LoginUser loginUser,
                                 String question,
                                 String knowledgeBaseKeyword,
                                 String documentKeyword,
                                 Integer topK);

    String searchStructuredFact(LoginUser loginUser,
                                String question,
                                String knowledgeBaseKeyword,
                                Integer topK);
}
