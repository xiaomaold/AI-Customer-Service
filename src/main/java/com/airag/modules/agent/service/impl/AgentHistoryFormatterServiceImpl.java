package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentHistoryFormatterService;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.MessageRoleEnum;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentHistoryFormatterServiceImpl implements AgentHistoryFormatterService {

    @Override
    public String formatHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "暂无历史会话。";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (!shouldIncludeInHistory(message)) {
                continue;
            }
            builder.append("[")
                    .append(message.getRole())
                    .append("] ")
                    .append(message.getContent())
                    .append("\n");
        }
        return builder.isEmpty() ? "暂无历史会话。" : builder.toString();
    }

    private boolean shouldIncludeInHistory(ChatMessage message) {
        if (message == null || StrUtil.isBlank(message.getContent())) {
            return false;
        }
        if (!MessageRoleEnum.ASSISTANT.getCode().equals(message.getRole())) {
            return true;
        }
        String content = message.getContent();
        return !content.contains("未登录或登录状态已失效")
                && !content.contains("回答生成失败")
                && !content.contains("Agent SSE failed")
                && !content.contains("DOCUMENT_NAMES_INCLUDED")
                && !content.contains("RESULT_TYPE:")
                && !content.contains("MATCHED_COUNT:")
                && !content.contains("MATCHED_BASE_COUNT:")
                && !content.contains("SEARCH_SCOPE:");
    }
}
