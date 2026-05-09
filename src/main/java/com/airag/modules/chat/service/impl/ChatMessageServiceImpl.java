package com.airag.modules.chat.service.impl;

import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.mapper.ChatMessageMapper;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.vo.ChatMessageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public void save(ChatMessage chatMessage) {
        chatMessageMapper.insert(chatMessage);
    }

    @Override
    public List<ChatMessage> listRecentMessages(Long sessionId, int limit) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("limit " + limit))
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getCreateTime))
                .toList();
    }

    @Override
    public List<ChatMessageVO> listSessionMessages(Long userId, Long sessionId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime))
                .stream()
                .map(message -> ChatMessageVO.builder()
                        .id(message.getId())
                        .sessionId(message.getSessionId())
                        .userId(message.getUserId())
                        .role(message.getRole())
                        .content(message.getContent())
                        .referenceContent(message.getReferenceContent())
                        .modelName(message.getModelName())
                        .createTime(message.getCreateTime())
                        .build())
                .toList();
    }
}
