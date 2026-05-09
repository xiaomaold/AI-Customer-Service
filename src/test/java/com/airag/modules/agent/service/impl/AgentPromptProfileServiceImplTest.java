package com.airag.modules.agent.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptProfileServiceImplTest {

    private final AgentPromptProfileServiceImpl service = new AgentPromptProfileServiceImpl();

    @Test
    void shouldKeepBasePromptWhenExecutionProfileIsBlank() {
        assertEquals("base prompt", service.buildSystemPrompt("base prompt", null, null));
        assertEquals("base prompt", service.buildSystemPrompt("base prompt", " ", " "));
    }

    @Test
    void shouldAppendDocumentQaInstruction() {
        String prompt = service.buildSystemPrompt("base prompt", "DOCUMENT_QA", null);

        assertTrue(prompt.startsWith("base prompt"));
        assertTrue(prompt.contains("document QA request"));
        assertTrue(prompt.contains("currently selected or uploaded document"));
    }

    @Test
    void shouldAppendStructuredFactInstruction() {
        String prompt = service.buildSystemPrompt("base prompt", "STRUCTURED_FACT", null);

        assertTrue(prompt.startsWith("base prompt"));
        assertTrue(prompt.contains("structured fact request"));
        assertTrue(prompt.contains("field-oriented answers"));
    }

    @Test
    void shouldAppendExecutionDirective() {
        String prompt = service.buildSystemPrompt("base prompt", "DOCUMENT_QA", "Stay inside the uploaded file.");

        assertTrue(prompt.contains("[Strategy execution directive]"));
        assertTrue(prompt.contains("Stay inside the uploaded file."));
    }
}
