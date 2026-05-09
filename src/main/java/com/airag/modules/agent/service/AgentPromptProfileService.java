package com.airag.modules.agent.service;

public interface AgentPromptProfileService {

    String buildSystemPrompt(String basePrompt, String executionProfile, String executionDirective);
}
