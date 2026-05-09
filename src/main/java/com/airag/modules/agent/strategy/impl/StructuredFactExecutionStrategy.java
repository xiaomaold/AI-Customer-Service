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
@Order(30)
@RequiredArgsConstructor
public class StructuredFactExecutionStrategy implements AgentExecutionStrategy {

    private final AgentChatService agentChatService;

    @Override
    public boolean supports(AgentTask task, TaskPlan plan) {
        return task.taskType() == TaskType.STRUCTURED_FACT_QUERY;
    }

    @Override
    public String strategyName() {
        return "STRUCTURED_FACT";
    }

    @Override
    public StrategyExecutionResult execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser) {
        request.setExecutionProfile("STRUCTURED_FACT");
        request.setExecutionDirective("Return only explicit structured facts, prefer phone/email/address/date style fields, and say not confirmed when evidence is missing.");
        return StrategyExecutionResult.builder()
                .emitter(agentChatService.streamChat(request))
                .resultStatus(AgentResultStatus.STREAM_STARTED)
                .outputSummary("Delegated to structured fact flow with field-oriented directive")
                .build();
    }
}
