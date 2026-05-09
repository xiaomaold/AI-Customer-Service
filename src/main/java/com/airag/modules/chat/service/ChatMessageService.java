package com.airag.modules.chat.service;

import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.vo.ChatMessageVO;

import java.util.List;

public interface ChatMessageService {

    void save(ChatMessage chatMessage);

    List<ChatMessage> listRecentMessages(Long sessionId, int limit);

    List<ChatMessageVO> listSessionMessages(Long userId, Long sessionId);
}
