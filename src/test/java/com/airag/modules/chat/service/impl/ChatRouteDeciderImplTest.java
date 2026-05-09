package com.airag.modules.chat.service.impl;

import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.routing.ActionRequestType;
import com.airag.modules.chat.routing.CustomerServiceDomain;
import com.airag.modules.chat.routing.CustomerServiceIntent;
import com.airag.modules.chat.routing.impl.DefaultBusinessSignalAnalyzer;
import com.airag.modules.chat.routing.impl.DefaultEnterpriseNeedClassifier;
import com.airag.modules.chat.routing.impl.DefaultQuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.impl.DefaultRouteRuleEngine;
import com.airag.modules.chat.routing.impl.DefaultSentencePatternAnalyzer;
import com.airag.modules.chat.service.ChatRouteDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatRouteDeciderImplTest {

    private final ChatRouteDeciderImpl decider =
            new ChatRouteDeciderImpl(new DefaultQuestionIntentFeatureExtractor(
                    new DefaultSentencePatternAnalyzer(),
                    new DefaultBusinessSignalAnalyzer(),
                    new DefaultEnterpriseNeedClassifier(),
                    new DefaultRouteRuleEngine()
            ));

    @Test
    void shouldRouteGeneralGenerationQuestionToGeneralGeneration() {
        ChatSendRequest request = request("帮我写一个通用通知模板");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("GENERATION_REQUESTED", decision.routeReason());
        assertEquals(CustomerServiceIntent.FORM_GENERATION, decision.serviceIntent());
    }

    @Test
    void shouldRouteShortKnowledgeTopicToUnifiedAgent() {
        ChatSendRequest request = request("退款规则");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("KNOWLEDGE_REQUIRED", decision.routeReason());
        assertEquals(CustomerServiceDomain.REFUND_AND_RETURNS, decision.serviceDomain());
    }

    @Test
    void shouldRouteBusinessGenerationToHybridGeneration() {
        ChatSendRequest request = request("根据公司请假制度帮我写请假条");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.HYBRID_GENERATION, decision.routeMode());
        assertEquals("KNOWLEDGE_AND_GENERATION", decision.routeReason());
    }

    @Test
    void shouldRouteDocumentDiscoveryQuestionToUnifiedAgent() {
        ChatSendRequest request = request("有哪些实验报告文档");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("DOCUMENT_DISCOVERY", decision.routeReason());
    }

    @Test
    void shouldRouteStructuredFactQuestionToUnifiedAgent() {
        ChatSendRequest request = request("客服电话是多少");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("STRUCTURED_FACT_QUERY", decision.routeReason());
        assertEquals(CustomerServiceIntent.STRUCTURED_FACT_QUERY, decision.serviceIntent());
    }

    @Test
    void shouldRouteProductQueryAsActionRequest() {
        ChatSendRequest request = request("我要查看 P-1002 产品信息");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(CustomerServiceIntent.PRODUCT_QUERY, decision.serviceIntent());
        assertEquals(ActionRequestType.PRODUCT_QUERY, decision.actionRequestType());
    }

    @Test
    void shouldRouteOrderSubmissionAsActionRequest() {
        ChatSendRequest request = request("帮我下单 P-1001 2个");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(CustomerServiceIntent.ORDER_SUBMISSION, decision.serviceIntent());
        assertEquals(ActionRequestType.ORDER_SUBMISSION, decision.actionRequestType());
    }

    @Test
    void shouldRouteRefundRequestAsActionRequest() {
        ChatSendRequest request = request("帮我申请退款");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(ActionRequestType.REFUND_REQUEST, decision.actionRequestType());
    }

    @Test
    void shouldRouteLeaveRequestAsActionRequest() {
        ChatSendRequest request = request("我要请假");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(CustomerServiceIntent.WORK_ORDER_SUBMISSION, decision.serviceIntent());
        assertEquals(ActionRequestType.WORK_ORDER_SUBMISSION, decision.actionRequestType());
    }

    @Test
    void shouldRouteStatusQueryAsActionRequest() {
        ChatSendRequest request = request("帮我查看退款进度");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(ActionRequestType.STATUS_QUERY, decision.actionRequestType());
    }

    @Test
    void shouldClarifyRefundQuestionWithoutExplicitRuleOrAction() {
        ChatSendRequest request = request("我要退款，需要怎么做");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("BUSINESS_CLARIFICATION", decision.routeReason());
        assertEquals(CustomerServiceDomain.REFUND_AND_RETURNS, decision.serviceDomain());
        assertEquals(ActionRequestType.NONE, decision.actionRequestType());
    }

    @Test
    void shouldClarifyRefundWhenOnlyOrderNoAndRefundAreProvided() {
        ChatSendRequest request = request("ORD172678607091666945退款");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("BUSINESS_CLARIFICATION", decision.routeReason());
        assertEquals(ActionRequestType.NONE, decision.actionRequestType());
    }

    @Test
    void shouldContinueRefundClarificationToKnowledgeQuery() {
        ChatSendRequest request = request("查规则");
        ChatMessage previousUser = new ChatMessage();
        previousUser.setRole("user");
        previousUser.setContent("退款");
        ChatMessage previousAssistant = new ChatMessage();
        previousAssistant.setRole("assistant");
        previousAssistant.setReferenceContent("ROUTE_MODE: GENERAL_GENERATION\nROUTE_REASON: BUSINESS_CLARIFICATION");

        ChatRouteDecision decision = decider.decide(request, List.of(previousUser, previousAssistant));

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("KNOWLEDGE_REQUIRED", decision.routeReason());
        assertEquals(ActionRequestType.NONE, decision.actionRequestType());
    }

    @Test
    void shouldContinueRefundClarificationToRefundAction() {
        ChatSendRequest request = request("申请退款");
        ChatMessage previousUser = new ChatMessage();
        previousUser.setRole("user");
        previousUser.setContent("退款");
        ChatMessage previousAssistant = new ChatMessage();
        previousAssistant.setRole("assistant");
        previousAssistant.setReferenceContent("ROUTE_MODE: GENERAL_GENERATION\nROUTE_REASON: BUSINESS_CLARIFICATION");

        ChatRouteDecision decision = decider.decide(request, List.of(previousUser, previousAssistant));

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(ActionRequestType.REFUND_REQUEST, decision.actionRequestType());
    }

    @Test
    void shouldContinueRefundClarificationToGenericKnowledgeChoice() {
        ChatSendRequest request = request("查规则");
        ChatMessage previousUser = new ChatMessage();
        previousUser.setRole("user");
        previousUser.setContent("退款");
        ChatMessage previousAssistant = new ChatMessage();
        previousAssistant.setRole("assistant");
        previousAssistant.setReferenceContent("ROUTE_MODE: GENERAL_GENERATION\nROUTE_REASON: BUSINESS_CLARIFICATION");

        ChatRouteDecision decision = decider.decide(request, List.of(previousUser, previousAssistant));

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("KNOWLEDGE_REQUIRED", decision.routeReason());
    }

    @Test
    void shouldContinueRefundClarificationToGenericActionChoice() {
        ChatSendRequest request = request("办理");
        ChatMessage previousUser = new ChatMessage();
        previousUser.setRole("user");
        previousUser.setContent("退款");
        ChatMessage previousAssistant = new ChatMessage();
        previousAssistant.setRole("assistant");
        previousAssistant.setReferenceContent("ROUTE_MODE: GENERAL_GENERATION\nROUTE_REASON: BUSINESS_CLARIFICATION");

        ChatRouteDecision decision = decider.decide(request, List.of(previousUser, previousAssistant));

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(ActionRequestType.REFUND_REQUEST, decision.actionRequestType());
    }

    @Test
    void shouldRouteGreetingToGeneralGeneration() {
        ChatSendRequest request = request("你好");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("CASUAL_CHAT", decision.routeReason());
    }

    @Test
    void shouldRouteLowSignalQuestionToGeneralGeneration() {
        ChatSendRequest request = request("111");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("INVALID_INPUT", decision.routeReason());
    }

    @Test
    void shouldClarifyAmbiguousRefundBeforeExplicitKnowledgeBaseSelection() {
        ChatSendRequest request = request("退款");
        request.setKnowledgeBaseId(1L);

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("BUSINESS_CLARIFICATION", decision.routeReason());
        assertEquals(ActionRequestType.NONE, decision.actionRequestType());
    }

    @Test
    void shouldRouteExplicitRefundActionBeforeExplicitKnowledgeBaseSelection() {
        ChatSendRequest request = request("我要退款");
        request.setKnowledgeBaseId(1L);

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("ACTION_REQUEST", decision.routeReason());
        assertEquals(ActionRequestType.REFUND_REQUEST, decision.actionRequestType());
    }

    @Test
    void shouldRouteExplicitRefundRuleToKnowledgeEvenWithBusinessKeyword() {
        ChatSendRequest request = request("退款规则");
        request.setKnowledgeBaseId(1L);

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("EXPLICIT_KNOWLEDGE_BASE", decision.routeReason());
    }

    @Test
    void shouldRespectExplicitKnowledgeBaseSelection() {
        ChatSendRequest request = request("公司报销制度");
        request.setKnowledgeBaseId(1L);

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("EXPLICIT_KNOWLEDGE_BASE", decision.routeReason());
    }

    @Test
    void shouldRouteCarryoverDocumentQuestionToUnifiedAgent() {
        ChatSendRequest request = request("主要内容是什么");
        request.setCarryoverDocumentName("图像的几何运算实验报告");
        request.setCarryoverKnowledgeBaseName("实验报告库");

        ChatRouteDecision decision = decider.decide(request, List.of());

        assertEquals(ChatRouteModeEnum.UNIFIED_AGENT, decision.routeMode());
        assertEquals("DOCUMENT_CARRYOVER", decision.routeReason());
    }

    @Test
    void shouldContinueGeneralGenerationForShortFollowUpQuestion() {
        ChatSendRequest request = request("退款具体一点");
        ChatMessage previousAssistant = new ChatMessage();
        previousAssistant.setRole("assistant");
        previousAssistant.setReferenceContent("ROUTE_MODE: GENERAL_GENERATION");

        ChatRouteDecision decision = decider.decide(request, List.of(previousAssistant));

        assertEquals(ChatRouteModeEnum.GENERAL_GENERATION, decision.routeMode());
        assertEquals("FOLLOW_UP_CONTINUATION", decision.routeReason());
    }

    private ChatSendRequest request(String question) {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion(question);
        return request;
    }
}
