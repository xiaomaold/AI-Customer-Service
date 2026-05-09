package com.airag.modules.agent.service;

import lombok.Builder;

@Builder
public record AgentSynthesisResult(String finalAnswer,
                                   String finalReferenceContent,
                                   boolean answerReplaced) {
}
