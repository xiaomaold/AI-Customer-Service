package com.airag.modules.chat.prompt.impl;

import com.airag.modules.chat.prompt.PromptTemplateService;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private static final String UNIFIED_AGENT_PROMPT_PATH = "classpath:prompts/unified-agent-system.txt";
    private static final String RAG_PROMPT_PATH = "classpath:prompts/rag-system.txt";
    private static final String GENERAL_GENERATION_PROMPT_PATH = "classpath:prompts/general-generation-system.txt";
    private static final String AI_DOCUMENT_ANALYSIS_PROMPT_PATH = "classpath:prompts/ai-document-analysis-system.txt";

    private final String unifiedAgentSystemPrompt;
    private final String ragSystemPrompt;
    private final String generalGenerationSystemPrompt;
    private final String aiDocumentAnalysisSystemPrompt;

    public PromptTemplateServiceImpl(ResourceLoader resourceLoader) {
        this.unifiedAgentSystemPrompt = loadPrompt(resourceLoader, UNIFIED_AGENT_PROMPT_PATH);
        this.ragSystemPrompt = loadPrompt(resourceLoader, RAG_PROMPT_PATH);
        this.generalGenerationSystemPrompt = loadPrompt(resourceLoader, GENERAL_GENERATION_PROMPT_PATH);
        this.aiDocumentAnalysisSystemPrompt = loadPrompt(resourceLoader, AI_DOCUMENT_ANALYSIS_PROMPT_PATH);
    }

    @Override
    public String unifiedAgentSystemPrompt() {
        return unifiedAgentSystemPrompt;
    }

    @Override
    public String ragSystemPrompt() {
        return ragSystemPrompt;
    }

    @Override
    public String generalGenerationSystemPrompt() {
        return generalGenerationSystemPrompt;
    }

    @Override
    public String aiDocumentAnalysisSystemPrompt() {
        return aiDocumentAnalysisSystemPrompt;
    }

    private String loadPrompt(ResourceLoader resourceLoader, String location) {
        try (InputStream inputStream = resourceLoader.getResource(location).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load prompt template: " + location, exception);
        }
    }
}
