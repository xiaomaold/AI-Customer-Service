package com.airag.modules.agent.service;

public interface AgentAnswerSynthesisService {

    AgentSynthesisResult synthesize(String originalQuestion,
                                    String history,
                                    String effectiveQuestion,
                                    String answer,
                                    String executionProfile,
                                    String referenceContent,
                                    RetryableAnswerGenerator retryableAnswerGenerator) throws InterruptedException;

    @FunctionalInterface
    interface RetryableAnswerGenerator {

        String generate(String retryQuestion) throws InterruptedException;
    }
}
