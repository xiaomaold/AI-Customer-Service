package com.airag.modules.chat.service;

import com.airag.modules.chat.dto.CreateSessionRequest;
import com.airag.modules.chat.dto.RenameSessionRequest;
import com.airag.modules.chat.entity.ChatSession;
import com.airag.modules.chat.vo.ChatSessionVO;

import java.util.List;

public interface ChatSessionService {

    ChatSessionVO createSession(Long userId, CreateSessionRequest request);

    List<ChatSessionVO> listByUserId(Long userId);

    ChatSessionVO renameSession(Long userId, Long sessionId, RenameSessionRequest request);

    ChatSessionVO togglePinSession(Long userId, Long sessionId);

    void deleteSession(Long userId, Long sessionId);

    ChatSession getValidSession(Long userId, Long sessionId);

    void refreshSessionActiveTime(Long sessionId);

    void updateSessionTitleIfNeeded(Long userId, Long sessionId, String question);
}
