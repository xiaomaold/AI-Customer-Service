package com.airag.modules.chat.service;

import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;

import java.util.List;

public interface ChatRouteDecider {

    ChatRouteDecision decide(ChatSendRequest request, List<ChatMessage> recentMessages);
}
