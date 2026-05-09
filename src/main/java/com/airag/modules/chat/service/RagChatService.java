package com.airag.modules.chat.service;

import com.airag.modules.chat.dto.ChatSendRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagChatService {

    SseEmitter streamChat(ChatSendRequest request);
}
