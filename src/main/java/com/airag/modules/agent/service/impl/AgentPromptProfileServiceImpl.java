package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentPromptProfileService;
import org.springframework.stereotype.Service;

@Service
public class AgentPromptProfileServiceImpl implements AgentPromptProfileService {

    @Override
    public String buildSystemPrompt(String basePrompt, String executionProfile, String executionDirective) {
        if (StrUtil.isBlank(basePrompt)) {
            return basePrompt;
        }

        StringBuilder promptBuilder = new StringBuilder(basePrompt);
        if (StrUtil.isNotBlank(executionProfile)) {
            String normalizedProfile = executionProfile.trim().toUpperCase();
            String profileInstruction = switch (normalizedProfile) {
                case "DOCUMENT_QA" -> """
                        [Execution profile instruction]
                        You are answering a document QA request.
                        Stay grounded in the currently selected or uploaded document.
                        Do not drift back to unrelated earlier session topics unless the current document explicitly requires it.
                        Prefer concise summaries that clearly reflect the document's main points.
                        """.trim();
                case "STRUCTURED_FACT" -> """
                        [Execution profile instruction]
                        You are answering a structured fact request.
                        Return only explicitly confirmed facts.
                        Prefer short field-oriented answers and avoid narrative expansion.
                        If a fact is not supported, say it is not confirmed.
                        """.trim();
                default -> null;
            };
            if (StrUtil.isNotBlank(profileInstruction)) {
                promptBuilder.append("\n\n").append(profileInstruction);
            }
        }
        if (StrUtil.isNotBlank(executionDirective)) {
            promptBuilder.append("\n\n[Strategy execution directive]\n")
                    .append(executionDirective.trim());
        }
        return promptBuilder.toString();
    }
}
