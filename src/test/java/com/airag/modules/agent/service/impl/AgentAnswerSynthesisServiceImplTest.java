package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.service.AgentAnswerPostProcessor;
import com.airag.modules.agent.service.AgentAnswerValidator;
import com.airag.modules.agent.service.AgentSynthesisResult;
import com.airag.modules.chat.service.ChatAnswerPresentationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentAnswerSynthesisServiceImplTest {

    @Test
    void shouldEnrichReferenceAndKeepAnswerWhenValidationPasses() throws InterruptedException {
        AgentAnswerValidator validator = mock(AgentAnswerValidator.class);
        AgentAnswerPostProcessor postProcessor = mock(AgentAnswerPostProcessor.class);
        ChatAnswerPresentationService presentationService = mock(ChatAnswerPresentationService.class);
        AgentAnswerSynthesisServiceImpl service =
                new AgentAnswerSynthesisServiceImpl(validator, postProcessor, presentationService);

        when(validator.validate("question", "draft answer"))
                .thenReturn(new AgentAnswerValidator.ValidationResult(true, false, "draft answer", null));
        when(postProcessor.finalizeAnswer("question", "effective question", "draft answer")).thenReturn("final answer");
        when(presentationService.enrichReferenceContent("question", "final answer", "raw reference")).thenReturn("final reference");

        AgentSynthesisResult result = service.synthesize(
                "question",
                "history",
                "effective question",
                "draft answer",
                "KNOWLEDGE_QA",
                "raw reference",
                retryQuestion -> {
                    throw new AssertionError("should not retry");
                }
        );

        assertEquals("final answer", result.finalAnswer());
        assertEquals("final reference", result.finalReferenceContent());
        assertTrue(result.answerReplaced());
    }

    @Test
    void shouldRetryWhenValidationRequestsRetry() throws InterruptedException {
        AgentAnswerValidator validator = mock(AgentAnswerValidator.class);
        AgentAnswerPostProcessor postProcessor = mock(AgentAnswerPostProcessor.class);
        ChatAnswerPresentationService presentationService = mock(ChatAnswerPresentationService.class);
        AgentAnswerSynthesisServiceImpl service =
                new AgentAnswerSynthesisServiceImpl(validator, postProcessor, presentationService);

        AgentAnswerValidator.ValidationResult initial =
                new AgentAnswerValidator.ValidationResult(false, true, "draft answer", "too vague");
        when(validator.validate("question", "draft answer")).thenReturn(initial);
        when(validator.buildRetryQuestion("question", initial)).thenReturn("retry with evidence");
        when(validator.validate("question", "retried answer"))
                .thenReturn(new AgentAnswerValidator.ValidationResult(true, false, "retried answer", null));
        when(postProcessor.finalizeAnswer("question", "effective question", "retried answer")).thenReturn("final retried answer");
        when(presentationService.enrichReferenceContent("question", "final retried answer", "raw reference")).thenReturn("final reference");

        AgentSynthesisResult result = service.synthesize(
                "question",
                "history",
                "effective question",
                "draft answer",
                "KNOWLEDGE_QA",
                "raw reference",
                retryQuestion -> {
                    assertEquals("retry with evidence", retryQuestion);
                    return "retried answer";
                }
        );

        assertEquals("final retried answer", result.finalAnswer());
        assertEquals("final reference", result.finalReferenceContent());
        verify(validator).buildRetryQuestion(eq("question"), any(AgentAnswerValidator.ValidationResult.class));
    }

    @Test
    void shouldUseFallbackWhenRetriedAnswerStillInvalid() throws InterruptedException {
        AgentAnswerValidator validator = mock(AgentAnswerValidator.class);
        AgentAnswerPostProcessor postProcessor = mock(AgentAnswerPostProcessor.class);
        ChatAnswerPresentationService presentationService = mock(ChatAnswerPresentationService.class);
        AgentAnswerSynthesisServiceImpl service =
                new AgentAnswerSynthesisServiceImpl(validator, postProcessor, presentationService);

        AgentAnswerValidator.ValidationResult initial =
                new AgentAnswerValidator.ValidationResult(false, true, "draft answer", "too vague");
        when(validator.validate("question", "draft answer")).thenReturn(initial);
        when(validator.buildRetryQuestion("question", initial)).thenReturn("retry with evidence");
        when(validator.validate("question", "retried answer"))
                .thenReturn(new AgentAnswerValidator.ValidationResult(false, false, "retried answer", "still bad"));
        when(postProcessor.buildFallbackAnswer("question", "retried answer")).thenReturn("fallback answer");
        when(postProcessor.finalizeAnswer("question", "effective question", "fallback answer")).thenReturn("final fallback answer");
        when(presentationService.enrichReferenceContent("question", "final fallback answer", null)).thenReturn(null);

        AgentSynthesisResult result = service.synthesize(
                "question",
                "history",
                "effective question",
                "draft answer",
                "KNOWLEDGE_QA",
                null,
                retryQuestion -> "retried answer"
        );

        assertEquals("final fallback answer", result.finalAnswer());
        assertEquals(null, result.finalReferenceContent());
        verify(postProcessor).buildFallbackAnswer("question", "retried answer");
    }

    @Test
    void shouldTrimStructuredFactAnswerToCompactLines() throws InterruptedException {
        AgentAnswerValidator validator = mock(AgentAnswerValidator.class);
        AgentAnswerPostProcessor postProcessor = mock(AgentAnswerPostProcessor.class);
        ChatAnswerPresentationService presentationService = mock(ChatAnswerPresentationService.class);
        AgentAnswerSynthesisServiceImpl service =
                new AgentAnswerSynthesisServiceImpl(validator, postProcessor, presentationService);

        String verboseAnswer = "line1\nline2\nline3\nline4\nline5\nline6";
        when(validator.validate("question", verboseAnswer))
                .thenReturn(new AgentAnswerValidator.ValidationResult(true, false, verboseAnswer, null));
        when(postProcessor.finalizeAnswer("question", "effective question", verboseAnswer)).thenReturn(verboseAnswer);
        when(presentationService.enrichReferenceContent("question", "line1\nline2\nline3\nline4\nline5", "raw reference"))
                .thenReturn("final reference");

        AgentSynthesisResult result = service.synthesize(
                "question",
                "history",
                "effective question",
                verboseAnswer,
                "STRUCTURED_FACT",
                "raw reference",
                retryQuestion -> verboseAnswer
        );

        assertEquals("line1\nline2\nline3\nline4\nline5", result.finalAnswer());
    }

    @Test
    void shouldCollapseExtraBlankLinesForDocumentQa() throws InterruptedException {
        AgentAnswerValidator validator = mock(AgentAnswerValidator.class);
        AgentAnswerPostProcessor postProcessor = mock(AgentAnswerPostProcessor.class);
        ChatAnswerPresentationService presentationService = mock(ChatAnswerPresentationService.class);
        AgentAnswerSynthesisServiceImpl service =
                new AgentAnswerSynthesisServiceImpl(validator, postProcessor, presentationService);

        String answer = "paragraph1\n\n\nparagraph2";
        when(validator.validate("question", answer))
                .thenReturn(new AgentAnswerValidator.ValidationResult(true, false, answer, null));
        when(postProcessor.finalizeAnswer("question", "effective question", answer)).thenReturn(answer);
        when(presentationService.enrichReferenceContent("question", "paragraph1\n\nparagraph2", "raw reference"))
                .thenReturn("final reference");

        AgentSynthesisResult result = service.synthesize(
                "question",
                "history",
                "effective question",
                answer,
                "DOCUMENT_QA",
                "raw reference",
                retryQuestion -> answer
        );

        assertEquals("paragraph1\n\nparagraph2", result.finalAnswer());
    }
}
