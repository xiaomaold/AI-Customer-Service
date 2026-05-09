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
public class UnifiedAgentExecutionStrategy implements AgentExecutionStrategy {

    private final AgentChatService agentChatService;

    @Override
    public boolean supports(AgentTask task, TaskPlan plan) {
        return task.taskType() == TaskType.KNOWLEDGE_QA
                || task.taskType() == TaskType.HYBRID_SYNTHESIS;
    }

    @Override
    public String strategyName() {
        return "UNIFIED_AGENT";
    }

    @Override
    public StrategyExecutionResult execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser) {
        request.setExecutionProfile(task.taskType().name());
        return StrategyExecutionResult.builder()
                .emitter(agentChatService.streamChat(request))
                .resultStatus(AgentResultStatus.STREAM_STARTED)
                .outputSummary("Delegated to unified agent with taskType=" + task.taskType().name())
                .build();
    }
}
