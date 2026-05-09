package com.airag.modules.agent.service;

public interface AgentAnswerPostProcessor {

    String finalizeAnswer(String originalQuestion, String effectiveQuestion, String answer);

    String buildFallbackAnswer(String question, String answer);

    String buildMissReason(String finalAnswer, String effectiveQuestion);
}
