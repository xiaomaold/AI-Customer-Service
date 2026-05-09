package com.airag.modules.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.common.exception.BusinessException;
import com.airag.modules.chat.dto.CreateSessionRequest;
import com.airag.modules.chat.dto.RenameSessionRequest;
import com.airag.modules.chat.entity.ChatSession;
import com.airag.modules.chat.mapper.ChatSessionMapper;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.chat.vo.ChatSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final int SESSION_TITLE_MAX_LENGTH = 20;
    private static final String DEFAULT_SESSION_TITLE = "新建会话";
    private static final List<String> TITLE_PREFIXES = List.of(
            "请问", "帮我", "麻烦帮我", "我想了解", "我想知道", "介绍一下", "说说", "请介绍", "帮我看看"
    );
    private static final List<String> WEAK_FOLLOW_UP_PREFIXES = List.of(
            "再", "继续", "接着", "然后", "顺便", "另外", "那就", "那再"
    );
    private static final List<String> WEAK_FOLLOW_UP_PHRASES = List.of(
            "再简短一点", "再详细一点", "再正式一点", "再口语一点", "换个说法", "换一种说法",
            "重写一下", "润色一下", "精简一下", "补充一下", "继续", "接着说", "展开一点",
            "详细一点", "简短一点", "口语一点", "正式一点", "改成表格", "改成列表"
    );

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public ChatSessionVO createSession(Long userId, CreateSessionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession chatSession = new ChatSession();
        chatSession.setId(IdUtil.getSnowflakeNextId());
        chatSession.setUserId(userId);
        chatSession.setSessionName(StrUtil.blankToDefault(request.getSessionName(), DEFAULT_SESSION_TITLE));
        chatSession.setSessionStatus("ACTIVE");
        chatSession.setPinned(0);
        chatSession.setLastMessageTime(now);
        chatSession.setCreateTime(now);
        chatSession.setUpdateTime(now);
        chatSession.setDeleted(0);
        chatSessionMapper.insert(chatSession);
        return toVO(chatSession);
    }

    @Override
    public List<ChatSessionVO> listByUserId(Long userId) {
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getPinned)
                        .orderByDesc(ChatSession::getLastMessageTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public ChatSessionVO renameSession(Long userId, Long sessionId, RenameSessionRequest request) {
        ChatSession session = getValidSession(userId, sessionId);
        String nextSessionName = StrUtil.trim(request.getSessionName());
        if (StrUtil.isBlank(nextSessionName)) {
            throw new BusinessException("会话名称不能为空");
        }

        session.setSessionName(nextSessionName);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        return toVO(session);
    }

    @Override
    public ChatSessionVO togglePinSession(Long userId, Long sessionId) {
        ChatSession session = getValidSession(userId, sessionId);
        Integer currentPinned = session.getPinned();
        session.setPinned(currentPinned != null && currentPinned == 1 ? 0 : 1);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        return toVO(session);
    }

    @Override
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = getValidSession(userId, sessionId);
        chatSessionMapper.deleteById(session.getId());
    }

    @Override
    public ChatSession getValidSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return session;
    }

    @Override
    public void refreshSessionActiveTime(Long sessionId) {
        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getLastMessageTime, LocalDateTime.now())
                .set(ChatSession::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void updateSessionTitleIfNeeded(Long userId, Long sessionId, String question) {
        if (StrUtil.isBlank(question)) {
            return;
        }

        ChatSession session = getValidSession(userId, sessionId);
        if (shouldKeepCurrentTitle(session.getSessionName(), question)) {
            return;
        }

        String generatedTitle = buildTitleFromQuestion(question);
        if (StrUtil.isBlank(generatedTitle)) {
            return;
        }

        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getSessionName, generatedTitle)
                .set(ChatSession::getUpdateTime, LocalDateTime.now()));
    }

    private boolean shouldKeepCurrentTitle(String currentTitle, String question) {
        return !isDefaultTitle(currentTitle) && isWeakFollowUp(question);
    }

    private boolean isDefaultTitle(String sessionName) {
        return StrUtil.isBlank(sessionName)
                || DEFAULT_SESSION_TITLE.equals(sessionName)
                || sessionName.startsWith("新会话");
    }

    private boolean isWeakFollowUp(String question) {
        String normalized = normalizeQuestion(question);
        if (StrUtil.isBlank(normalized)) {
            return false;
        }
        if (normalized.length() <= 8 && WEAK_FOLLOW_UP_PREFIXES.stream().anyMatch(normalized::startsWith)) {
            return true;
        }
        return WEAK_FOLLOW_UP_PHRASES.stream().anyMatch(normalized::contains);
    }

    private String buildTitleFromQuestion(String question) {
        String normalized = normalizeQuestion(question);
        if (normalized.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        if (normalized.length() <= SESSION_TITLE_MAX_LENGTH) {
            return normalized;
        }
        return StrUtil.sub(normalized, 0, SESSION_TITLE_MAX_LENGTH) + "...";
    }

    private String normalizeQuestion(String question) {
        String normalized = question
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        normalized = stripTrailingPunctuation(normalized);

        for (String prefix : TITLE_PREFIXES) {
            if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                normalized = normalized.substring(prefix.length()).trim();
                break;
            }
        }
        return normalized;
    }

    private String stripTrailingPunctuation(String value) {
        String normalized = value;
        while (StrUtil.isNotBlank(normalized)) {
            String stripped = normalized
                    .replaceAll("[？?！!。；;、,，]+$", "")
                    .trim();
            if (stripped.equals(normalized)) {
                break;
            }
            normalized = stripped;
        }
        return normalized;
    }

    private ChatSessionVO toVO(ChatSession session) {
        return ChatSessionVO.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .sessionName(session.getSessionName())
                .sessionStatus(session.getSessionStatus())
                .pinned(session.getPinned())
                .lastMessageTime(session.getLastMessageTime())
                .createTime(session.getCreateTime())
                .build();
    }
}
