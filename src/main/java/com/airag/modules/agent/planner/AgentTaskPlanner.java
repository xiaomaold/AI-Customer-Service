package com.airag.modules.agent.planner;

import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;

public interface AgentTaskPlanner {

    TaskPlan plan(AgentTask task);
}
