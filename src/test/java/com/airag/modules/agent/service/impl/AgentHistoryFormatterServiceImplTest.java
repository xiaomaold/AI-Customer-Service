package com.airag.modules.agent.service.impl;

import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.MessageRoleEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHistoryFormatterServiceImplTest {

    private final AgentHistoryFormatterServiceImpl service = new AgentHistoryFormatterServiceImpl();

    @Test
    void shouldFormatUsableConversationHistory() {
        ChatMessage user = new ChatMessage();
        user.setRole(MessageRoleEnum.USER.getCode());
        user.setContent("你好");

        ChatMessage assistant = new ChatMessage();
        assistant.setRole(MessageRoleEnum.ASSISTANT.getCode());
        assistant.setContent("请问有什么可以帮你");

        String history = service.formatHistory(List.of(user, assistant));

        assertTrue(history.contains("[user] 你好"));
        assertTrue(history.contains("[assistant] 请问有什么可以帮你"));
    }

    @Test
    void shouldSkipInternalToolLikeAssistantMessages() {
        ChatMessage assistant = new ChatMessage();
        assistant.setRole(MessageRoleEnum.ASSISTANT.getCode());
        assistant.setContent("MATCHED_COUNT: 3");

        String history = service.formatHistory(List.of(assistant));

        assertFalse(history.contains("MATCHED_COUNT: 3"));
    }
}
