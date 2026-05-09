package com.airag.modules.chat.prompt;

public interface PromptTemplateService {

    String unifiedAgentSystemPrompt();

    String ragSystemPrompt();

    String generalGenerationSystemPrompt();

    String aiDocumentAnalysisSystemPrompt();
}
