package com.airag.modules.agent.strategy;

import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;

public interface AgentExecutionStrategy {

    boolean supports(AgentTask task, TaskPlan plan);

    String strategyName();

    StrategyExecutionResult execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser);
}
