package com.airag.modules.agent.context;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RecentConversationContext {

    String currentQuestion;
    String knowledgeBaseName;
    String documentName;
    Long sourceMessageId;
    boolean explicitKnowledgeBaseInQuestion;
    boolean explicitDocumentInQuestion;
    boolean applyKnowledgeBaseCarryover;
    boolean applyDocumentCarryover;

    public static RecentConversationContext empty() {
        return RecentConversationContext.builder().build();
    }

    public boolean hasKnowledgeBase() {
        return knowledgeBaseName != null && !knowledgeBaseName.isBlank();
    }

    public boolean hasDocument() {
        return documentName != null && !documentName.isBlank();
    }

    public boolean hasCarryover() {
        return applyKnowledgeBaseCarryover || applyDocumentCarryover;
    }
}
