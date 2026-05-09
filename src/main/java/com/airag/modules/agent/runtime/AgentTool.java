package com.airag.modules.agent.runtime;

import com.airag.modules.agent.task.TaskStep;

public interface AgentTool {

    boolean supports(TaskStep step);

    String toolName();

    AgentToolExecutionResult execute(AgentToolExecutionContext context, TaskStep step);
}
