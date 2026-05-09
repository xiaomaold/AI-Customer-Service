package com.airag.modules.agent.service;

import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentOrchestratorService {

    SseEmitter streamChat(ChatSendRequest request, LoginUser loginUser);
}
