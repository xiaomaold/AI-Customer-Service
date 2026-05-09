package com.airag.modules.agent.service;

public interface AgentAnswerValidator {

    ValidationResult validate(String question, String answer);

    String buildRetryQuestion(String originalQuestion, ValidationResult validationResult);

    record ValidationResult(boolean valid, boolean needsRetry, String answer, String reason) {
    }
}
