package com.airag.modules.agent.strategy.impl;

import com.airag.modules.agent.strategy.AgentExecutionStrategy;
import com.airag.modules.agent.strategy.StrategyExecutionResult;
import com.airag.modules.agent.trace.AgentResultStatus;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskType;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.service.GeneralGenerationChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
public class GeneralGenerationExecutionStrategy implements AgentExecutionStrategy {

    private final GeneralGenerationChatService generalGenerationChatService;

    @Override
    public boolean supports(AgentTask task, TaskPlan plan) {
        return task.taskType() == TaskType.DIRECT_CHAT;
    }

    @Override
    public String strategyName() {
        return "GENERAL_GENERATION";
    }

    @Override
    public StrategyExecutionResult execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser) {
        request.setExecutionProfile("DIRECT_CHAT");
        return StrategyExecutionResult.builder()
                .emitter(generalGenerationChatService.streamChat(request, loginUser))
                .resultStatus(AgentResultStatus.STREAM_STARTED)
                .outputSummary("Delegated to general generation with DIRECT_CHAT profile")
                .build();
    }
}
