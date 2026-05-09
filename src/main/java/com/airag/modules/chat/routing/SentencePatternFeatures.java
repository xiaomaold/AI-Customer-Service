package com.airag.modules.chat.routing;

public record SentencePatternFeatures(
        boolean asksDefinition,
        boolean asksPrinciple,
        boolean asksComparison,
        boolean asksReason,
        boolean asksGeneration,
        boolean asksDocumentDiscovery,
        boolean asksKnowledgeBaseList,
        boolean followUpRewrite,
        boolean casualChat
) {
}
