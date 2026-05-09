package com.airag.modules.agent.service;

import com.airag.modules.chat.entity.ChatMessage;

import java.util.List;

public interface AgentHistoryFormatterService {

    String formatHistory(List<ChatMessage> messages);
}
