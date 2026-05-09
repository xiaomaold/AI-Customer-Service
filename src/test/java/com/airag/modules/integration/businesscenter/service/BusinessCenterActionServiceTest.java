package com.airag.modules.integration.businesscenter.service;

import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.integration.businesscenter.client.BusinessCenterClient;
import com.airag.modules.integration.businesscenter.dto.CreateOrderCommand;
import com.airag.modules.integration.businesscenter.dto.CreateWorkOrderCommand;
import com.airag.modules.integration.businesscenter.dto.OrderRecord;
import com.airag.modules.integration.businesscenter.dto.ProductRecord;
import com.airag.modules.integration.businesscenter.dto.WorkOrderRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessCenterActionServiceTest {

    private BusinessCenterClient businessCenterClient;
    private BusinessCenterActionService actionService;

    @BeforeEach
    void setUp() {
        businessCenterClient = mock(BusinessCenterClient.class);
        actionService = new BusinessCenterActionService(businessCenterClient);
    }

    @Test
    void shouldAskForProductNoWhenQueryingProductWithoutProductNo() {
        ChatSendRequest request = request("PRODUCT_QUERY", "我要查看产品信息");

        String answer = actionService.handle(request, List.of());

        assertTrue(answer.contains("产品号"));
    }

    @Test
    void shouldQueryProductByProductNo() {
        when(businessCenterClient.getProduct("P-1002")).thenReturn(new ProductRecord(
                3002L, "P-1002", "无线鼠标", new BigDecimal("129.00"), "人体工学无线鼠标", null, null
        ));
        ChatSendRequest request = request("PRODUCT_QUERY", "我要查看 P-1002 产品信息");

        String answer = actionService.handle(request, List.of());

        verify(businessCenterClient).getProduct("P-1002");
        assertTrue(answer.contains("无线鼠标"));
        assertTrue(answer.contains("129"));
    }

    @Test
    void shouldAskForProductNoWhenCreatingOrderWithoutProductNo() {
        ChatSendRequest request = request("ORDER_SUBMISSION", "帮我下单");

        String answer = actionService.handle(request, List.of());

        assertTrue(answer.contains("产品号"));
    }

    @Test
    void shouldAskForQuantityWhenCreatingOrderWithoutQuantity() {
        ChatSendRequest request = request("ORDER_SUBMISSION", "我要下单P-1002");

        String answer = actionService.handle(request, List.of());

        assertTrue(answer.contains("购买数量"));
    }

    @Test
    void shouldCreateOrderWhenProductNoAndQuantityAreProvided() {
        when(businessCenterClient.createOrder(any(CreateOrderCommand.class))).thenReturn(new OrderRecord(
                1L, "ORD202604220010", 2001L, "CUSTOMER", 3001L, "P-1001", "商务笔记本电脑",
                new BigDecimal("5999.00"), 2, new BigDecimal("11998.00"), "UNPAID", null, "AI_CHAT", null, null
        ));
        ChatSendRequest request = request("ORDER_SUBMISSION", "帮我下单 P-1001 2个");

        String answer = actionService.handle(request, List.of());

        verify(businessCenterClient).createOrder(argThat(command -> command != null
                && "P-1001".equals(command.productNo())
                && Integer.valueOf(2).equals(command.quantity())));
        assertTrue(answer.contains("ORD202604220010"));
        assertTrue(answer.contains("未付款"));
    }

    @Test
    void shouldPreferCurrentProductNoOverHistoryWhenSubmittingOrder() {
        when(businessCenterClient.createOrder(any(CreateOrderCommand.class))).thenReturn(new OrderRecord(
                1L, "ORD202604220011", 2001L, "CUSTOMER", 3002L, "P-1002", "无线鼠标",
                new BigDecimal("129.00"), 1, new BigDecimal("129.00"), "UNPAID", null, "AI_CHAT", null, null
        ));
        ChatSendRequest request = request("ORDER_SUBMISSION", "我要买 P-1002 1个");
        ChatMessage history = new ChatMessage();
        history.setRole("user");
        history.setContent("我要买 P-1001 3个");

        String answer = actionService.handle(request, List.of(history));

        verify(businessCenterClient).createOrder(argThat(command -> command != null
                && "P-1002".equals(command.productNo())
                && Integer.valueOf(1).equals(command.quantity())));
        assertTrue(answer.contains("无线鼠标"));
        assertTrue(answer.contains("ORD202604220011"));
    }

    @Test
    void shouldNotReuseHistoryWhenFreshOrderRequestMissesQuantity() {
        ChatSendRequest request = request("ORDER_SUBMISSION", "我要下单P-1002");
        ChatMessage history = new ChatMessage();
        history.setRole("user");
        history.setContent("我要买 P-1001 3个");

        String answer = actionService.handle(request, List.of(history));

        assertTrue(answer.contains("购买数量"));
    }

    @Test
    void shouldCreateLeaveWorkOrderWhenParametersAreComplete() {
        when(businessCenterClient.createWorkOrder(any(CreateWorkOrderCommand.class))).thenReturn(new WorkOrderRecord(
                1L, "WO202604220010", 1001L, "EMPLOYEE", "LEAVE", "PENDING", "员工请假申请",
                "我要请 3 天年假", null, Map.of("leaveDays", 3, "leaveType", "ANNUAL"),
                null, null, null, null, "AI_CHAT", null, null
        ));
        ChatSendRequest request = request("WORK_ORDER_SUBMISSION", "我要请 3 天年假");
        request.setUserId(1001L);

        String answer = actionService.handle(request, List.of());

        verify(businessCenterClient).createWorkOrder(any(CreateWorkOrderCommand.class));
        assertTrue(answer.contains("请假工单"));
        assertTrue(answer.contains("3 天"));
    }

    @Test
    void shouldAskForOrderNoWhenSubmittingRefund() {
        ChatSendRequest request = request("REFUND_REQUEST", "我要退款");

        String answer = actionService.handle(request, List.of());

        assertTrue(answer.contains("订单号"));
    }

    @Test
    void shouldQueryWorkOrderStatus() {
        when(businessCenterClient.getWorkOrder("WO202604220001")).thenReturn(new WorkOrderRecord(
                1L, "WO202604220001", 2001L, "CUSTOMER", "REFUND", "PROCESSING", "客户退款申请",
                "我要退款", "ORD202604220002", Map.of(), null, 9001L, "正在处理", null, "AI_CHAT", null, null
        ));
        ChatSendRequest request = request("STATUS_QUERY", "帮我查一下 WO202604220001 的进度");

        String answer = actionService.handle(request, List.of());

        verify(businessCenterClient).getWorkOrder("WO202604220001");
        assertTrue(answer.contains("处理中"));
        assertTrue(answer.contains("退款"));
    }

    private ChatSendRequest request(String routeAction, String question) {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(2001L);
        request.setRouteAction(routeAction);
        request.setQuestion(question);
        return request;
    }
}
