package com.airag.modules.agent.service;

import com.airag.modules.chat.dto.ChatSendRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentChatService {

    SseEmitter streamChat(ChatSendRequest request);
}
