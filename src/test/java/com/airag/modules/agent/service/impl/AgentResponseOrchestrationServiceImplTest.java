package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.service.AgentSynthesisResult;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentResponseOrchestrationServiceImplTest {

    @Test
    void shouldSaveAssistantMessageForDirectAnswer() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        AgentResponseOrchestrationServiceImpl service =
                new AgentResponseOrchestrationServiceImpl(chatSessionService, chatMessageService);
        ReflectionTestUtils.setField(service, "modelName", "qwen-turbo");

        ChatSendRequest request = request();

        service.handleDirectAnswerResponse(request, new SseEmitter(), "直接答案", "引用内容");

        verify(chatMessageService).save(any(ChatMessage.class));
        verify(chatSessionService).refreshSessionActiveTime(1L);
    }

    @Test
    void shouldSaveAssistantMessageForFinalAnswer() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        AgentResponseOrchestrationServiceImpl service =
                new AgentResponseOrchestrationServiceImpl(chatSessionService, chatMessageService);
        ReflectionTestUtils.setField(service, "modelName", "qwen-turbo");

        ChatSendRequest request = request();
        AgentSynthesisResult result = AgentSynthesisResult.builder()
                .finalAnswer("最终答案")
                .finalReferenceContent("最终引用")
                .answerReplaced(true)
                .build();

        service.handleFinalAnswerResponse(request, new SseEmitter(), "初始引用", result);

        verify(chatMessageService).save(any(ChatMessage.class));
        verify(chatSessionService).refreshSessionActiveTime(1L);
    }

    @Test
    void shouldHandleKnowledgeBaseListResponse() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        AgentResponseOrchestrationServiceImpl service =
                new AgentResponseOrchestrationServiceImpl(chatSessionService, chatMessageService);
        ReflectionTestUtils.setField(service, "modelName", "qwen-turbo");

        ChatSendRequest request = request();

        service.handleKnowledgeBaseListResponse(
                request,
                new SseEmitter(),
                List.of(KnowledgeBaseVO.builder().knowledgeBaseName("企业客服知识库").build())
        );

        verify(chatMessageService).save(any(ChatMessage.class));
        verify(chatSessionService).refreshSessionActiveTime(1L);
    }

    private ChatSendRequest request() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("测试问题");
        return request;
    }
}
