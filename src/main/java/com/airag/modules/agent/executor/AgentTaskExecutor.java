package com.airag.modules.agent.executor;

import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentTaskExecutor {

    SseEmitter execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser);
}
