package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.service.AgentAnswerPostProcessor;
import com.airag.modules.agent.service.AgentAnswerSynthesisService;
import com.airag.modules.agent.service.AgentAnswerValidator;
import com.airag.modules.agent.service.AgentSynthesisResult;
import com.airag.modules.chat.service.ChatAnswerPresentationService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAnswerSynthesisServiceImpl implements AgentAnswerSynthesisService {

    private final AgentAnswerValidator agentAnswerValidator;
    private final AgentAnswerPostProcessor agentAnswerPostProcessor;
    private final ChatAnswerPresentationService chatAnswerPresentationService;

    @Override
    public AgentSynthesisResult synthesize(String originalQuestion,
                                           String history,
                                           String effectiveQuestion,
                                           String answer,
                                           String executionProfile,
                                           String referenceContent,
                                           RetryableAnswerGenerator retryableAnswerGenerator) throws InterruptedException {
        AgentAnswerValidator.ValidationResult validationResult = agentAnswerValidator.validate(originalQuestion, answer);
        String finalAnswer = validationResult.answer();

        if (validationResult.needsRetry()) {
            log.warn("Agent answer validation triggered retry reason={}", validationResult.reason());
            String retryQuestion = agentAnswerValidator.buildRetryQuestion(originalQuestion, validationResult);
            String retriedAnswer = retryableAnswerGenerator.generate(retryQuestion);
            AgentAnswerValidator.ValidationResult retriedValidation = agentAnswerValidator.validate(originalQuestion, retriedAnswer);
            finalAnswer = retriedValidation.valid()
                    ? retriedValidation.answer()
                    : agentAnswerPostProcessor.buildFallbackAnswer(originalQuestion, retriedValidation.answer());
        }

        finalAnswer = agentAnswerPostProcessor.finalizeAnswer(originalQuestion, effectiveQuestion, finalAnswer);
        finalAnswer = applyExecutionProfile(executionProfile, finalAnswer);
        String finalReferenceContent = chatAnswerPresentationService.enrichReferenceContent(
                originalQuestion,
                finalAnswer,
                referenceContent
        );

        return AgentSynthesisResult.builder()
                .finalAnswer(finalAnswer)
                .finalReferenceContent(finalReferenceContent)
                .answerReplaced(!finalAnswer.equals(answer))
                .build();
    }

    private String applyExecutionProfile(String executionProfile, String answer) {
        if (StrUtil.isBlank(answer) || StrUtil.isBlank(executionProfile)) {
            return answer;
        }
        return switch (executionProfile) {
            case "STRUCTURED_FACT" -> normalizeStructuredFactAnswer(answer);
            case "DOCUMENT_QA" -> normalizeDocumentQaAnswer(answer);
            default -> answer;
        };
    }

    private String normalizeStructuredFactAnswer(String answer) {
        String[] lines = answer.split("\\R");
        StringBuilder builder = new StringBuilder();
        int appended = 0;
        for (String rawLine : lines) {
            String line = StrUtil.trim(rawLine);
            if (StrUtil.isBlank(line)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(line);
            appended++;
            if (appended >= 5) {
                break;
            }
        }
        return builder.isEmpty() ? answer.trim() : builder.toString();
    }

    private String normalizeDocumentQaAnswer(String answer) {
        return answer
                .replaceAll("(\\R\\s*){3,}", "\n\n")
                .trim();
    }
}
