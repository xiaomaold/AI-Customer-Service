package com.airag.modules.agent.strategy.impl;

import com.airag.modules.agent.service.AgentChatService;
import com.airag.modules.agent.strategy.AgentExecutionStrategy;
import com.airag.modules.agent.strategy.StrategyExecutionResult;
import com.airag.modules.agent.trace.AgentResultStatus;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class DocumentQaExecutionStrategy implements AgentExecutionStrategy {

    private final AgentChatService agentChatService;

    @Override
    public boolean supports(AgentTask task, TaskPlan plan) {
        return task.taskType() == TaskType.DOCUMENT_QA;
    }

    @Override
    public String strategyName() {
        return "DOCUMENT_QA";
    }

    @Override
    public StrategyExecutionResult execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser) {
        request.setExecutionProfile("DOCUMENT_QA");
        request.setExecutionDirective("Anchor the answer to the active document, prioritize document snippets over prior session memory, and summarize only what the current document supports.");
        if ((request.getCarryoverDocumentName() == null || request.getCarryoverDocumentName().isBlank())
                && task.conversationContext() != null
                && task.conversationContext().hasDocument()) {
            request.setCarryoverDocumentName(task.conversationContext().getDocumentName());
        }
        if ((request.getCarryoverKnowledgeBaseName() == null || request.getCarryoverKnowledgeBaseName().isBlank())
                && task.conversationContext() != null
                && task.conversationContext().hasKnowledgeBase()) {
            request.setCarryoverKnowledgeBaseName(task.conversationContext().getKnowledgeBaseName());
        }
        return StrategyExecutionResult.builder()
                .emitter(agentChatService.streamChat(request))
                .resultStatus(AgentResultStatus.STREAM_STARTED)
                .outputSummary("Delegated to document QA flow with documentCarryover="
                        + (request.getCarryoverDocumentName() == null ? "" : request.getCarryoverDocumentName()))
                .build();
    }
}
