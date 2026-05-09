package com.airag.modules.agent.service;

import lombok.Builder;

@Builder
public record AgentEvidenceBundle(String effectiveQuestion,
                                  String referenceContent,
                                  String directAnswer,
                                  boolean knowledgeEvidenceUsed,
                                  boolean documentEvidenceUsed,
                                  boolean directAnswerUsed) {
}
