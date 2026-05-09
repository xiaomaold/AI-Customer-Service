package com.airag.modules.chat.service.impl;

import com.airag.modules.agent.service.AgentOrchestratorService;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.routing.ActionRequestType;
import com.airag.modules.chat.routing.CustomerServiceDomain;
import com.airag.modules.chat.routing.CustomerServiceIntent;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatRouteDecider;
import com.airag.modules.chat.service.ChatRouteDecision;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.vo.UserKnowledgePermissionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoutingServiceImplTest {

    @Mock
    private AgentOrchestratorService agentOrchestratorService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatRouteDecider chatRouteDecider;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private ChatRoutingServiceImpl chatRoutingService;

    @Test
    void shouldAlwaysDelegateToAgentOrchestrator() {
        ChatSendRequest request = request("帮我写一个请假条");
        LoginUser loginUser = loginUser();
        when(chatMessageService.listRecentMessages(1L, 6)).thenReturn(List.of());
        when(chatRouteDecider.decide(eq(request), eq(List.of()))).thenReturn(new ChatRouteDecision(
                ChatRouteModeEnum.GENERAL_GENERATION,
                "GENERATION_REQUESTED",
                CustomerServiceDomain.OTHER,
                CustomerServiceIntent.FORM_GENERATION,
                ActionRequestType.NONE
        ));
        when(agentOrchestratorService.streamChat(eq(request), eq(loginUser))).thenReturn(new SseEmitter());

        chatRoutingService.streamChat(request, loginUser);

        verify(agentOrchestratorService).streamChat(eq(request), eq(loginUser));
        assertEquals("GENERAL_GENERATION", request.getRouteMode());
        assertEquals("GENERATION_REQUESTED", request.getRouteReason());
    }

    @Test
    void shouldDelegateEnterpriseQuestionToAgentOrchestrator() {
        ChatSendRequest request = request("退款规则是什么");
        LoginUser loginUser = loginUser();
        when(chatMessageService.listRecentMessages(1L, 6)).thenReturn(List.of(new ChatMessage()));
        when(chatRouteDecider.decide(eq(request), anyList())).thenReturn(new ChatRouteDecision(
                ChatRouteModeEnum.UNIFIED_AGENT,
                "DEFAULT_AGENT",
                CustomerServiceDomain.REFUND_AND_RETURNS,
                CustomerServiceIntent.RULE_QUERY,
                ActionRequestType.NONE
        ));
        when(agentOrchestratorService.streamChat(request, loginUser)).thenReturn(new SseEmitter());

        chatRoutingService.streamChat(request, loginUser);

        verify(agentOrchestratorService).streamChat(request, loginUser);
    }

    @Test
    void shouldInjectDefaultKnowledgeBaseForOrdinaryUserEnterpriseQuestion() {
        ChatSendRequest request = request("打印机无法打印怎么办");
        LoginUser loginUser = loginUser();
        when(chatMessageService.listRecentMessages(1L, 6)).thenReturn(List.of());
        when(chatRouteDecider.decide(eq(request), eq(List.of()))).thenReturn(new ChatRouteDecision(
                ChatRouteModeEnum.UNIFIED_AGENT,
                "DEFAULT_AGENT",
                CustomerServiceDomain.AFTER_SALES,
                CustomerServiceIntent.PROCESS_QUERY,
                ActionRequestType.NONE
        ));
        when(knowledgeBaseService.listAccessibleByUserId(1001L, List.of("USER"))).thenReturn(List.of(
                UserKnowledgePermissionVO.builder()
                        .knowledgeBaseId(123L)
                        .knowledgeBaseName("企业客服知识库")
                        .permissionType("READ")
                        .status("ACTIVE")
                        .build()
        ));
        when(agentOrchestratorService.streamChat(request, loginUser)).thenReturn(new SseEmitter());

        chatRoutingService.streamChat(request, loginUser);

        assertEquals(123L, request.getKnowledgeBaseId());
        verify(agentOrchestratorService).streamChat(request, loginUser);
    }

    @Test
    void shouldNotInjectDefaultKnowledgeBaseForGeneralGeneration() {
        ChatSendRequest request = request("我是谁");
        LoginUser loginUser = loginUser();
        when(chatMessageService.listRecentMessages(1L, 6)).thenReturn(List.of());
        when(chatRouteDecider.decide(eq(request), eq(List.of()))).thenReturn(new ChatRouteDecision(
                ChatRouteModeEnum.GENERAL_GENERATION,
                "GENERAL_REQUEST",
                CustomerServiceDomain.OTHER,
                CustomerServiceIntent.KNOWLEDGE_QA,
                ActionRequestType.NONE
        ));
        when(agentOrchestratorService.streamChat(eq(request), eq(loginUser))).thenReturn(new SseEmitter());

        chatRoutingService.streamChat(request, loginUser);

        assertNull(request.getKnowledgeBaseId());
        verify(agentOrchestratorService).streamChat(eq(request), eq(loginUser));
    }

    private ChatSendRequest request(String question) {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion(question);
        return request;
    }

    private LoginUser loginUser() {
        return LoginUser.builder()
                .userId(1001L)
                .username("tester")
                .roles(List.of("USER"))
                .build();
    }
}
