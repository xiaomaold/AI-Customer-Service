package com.airag.modules.integration.businesscenter.service;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.integration.businesscenter.client.BusinessCenterClient;
import com.airag.modules.integration.businesscenter.client.BusinessCenterClientException;
import com.airag.modules.integration.businesscenter.dto.CreateOrderCommand;
import com.airag.modules.integration.businesscenter.dto.CreateWorkOrderCommand;
import com.airag.modules.integration.businesscenter.dto.OrderRecord;
import com.airag.modules.integration.businesscenter.dto.ProductRecord;
import com.airag.modules.integration.businesscenter.dto.WorkOrderRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BusinessCenterActionService {

    private static final Pattern PRODUCT_NO_PATTERN = Pattern.compile("(?i)P-?\\d{3,}");
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?i)ORD\\d{6,}");
    private static final Pattern WORK_ORDER_NO_PATTERN = Pattern.compile("(?i)WO\\d{6,}");
    private static final Pattern LEAVE_DAYS_PATTERN = Pattern.compile("(\\d{1,2})\\s*天");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(?<![A-Z0-9-])(\\d{1,3})\\s*(个|件|台|份|张|单|套|部)(?![A-Z0-9-])");

    private final BusinessCenterClient businessCenterClient;

    public BusinessCenterActionService(BusinessCenterClient businessCenterClient) {
        this.businessCenterClient = businessCenterClient;
    }

    public String handle(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String routeAction = StrUtil.blankToDefault(request.getRouteAction(), "");
        if (StrUtil.isBlank(routeAction)) {
            return null;
        }
        return switch (routeAction) {
            case "PRODUCT_QUERY" -> handleProductQuery(request, recentMessages);
            case "ORDER_SUBMISSION" -> handleOrderSubmission(request, recentMessages);
            case "WORK_ORDER_SUBMISSION" -> handleWorkOrderSubmission(request, recentMessages);
            case "REFUND_REQUEST" -> handleRefundRequest(request, recentMessages);
            case "HUMAN_HANDOFF" -> handleHumanHandoff(request, recentMessages);
            case "STATUS_QUERY" -> handleStatusQuery(request, recentMessages);
            default -> null;
        };
    }

    private String handleOrderSubmission(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String productNo = resolveOrderProductNo(request.getQuestion(), recentMessages);
        Integer quantity = resolveOrderQuantity(request.getQuestion(), recentMessages);
        if (StrUtil.isBlank(productNo) || quantity == null || quantity <= 0) {
            return buildMissingOrderFieldsReply(productNo, quantity);
        }
        try {
            OrderRecord order = businessCenterClient.createOrder(new CreateOrderCommand(
                    request.getUserId(),
                    normalizeCode(productNo),
                    quantity,
                    "AI_CHAT"
            ));
            return "已为你提交订单。订单号：" + safe(order.orderNo())
                    + "；产品：" + safe(order.productNameSnapshot())
                    + "；数量：" + quantity
                    + "；状态：" + formatOrderStatus(order.status())
                    + "。当前付款还不能在 AI 侧完成，需要由后台人工处理。";
        } catch (BusinessCenterClientException exception) {
            return buildActionFailureReply("提交订单", exception);
        }
    }

    private String handleProductQuery(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String productNo = resolveLatestMatch(request.getQuestion(), recentMessages, PRODUCT_NO_PATTERN);
        if (StrUtil.isBlank(productNo)) {
            return "可以帮你查询产品信息。请先提供产品号，例如 P-1002。";
        }
        try {
            ProductRecord product = businessCenterClient.getProduct(normalizeCode(productNo));
            return "查询结果：产品号 " + safe(product.productNo())
                    + "，产品名称：" + safe(product.productName())
                    + "，价格：" + formatPrice(product.price())
                    + "，产品说明：" + safe(product.description()) + "。";
        } catch (BusinessCenterClientException exception) {
            return buildActionFailureReply("查询产品信息", exception);
        }
    }

    private String handleWorkOrderSubmission(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String conversationText = buildConversationText(request, recentMessages);
        if (!looksLikeLeaveRequest(conversationText)) {
            return "当前 AI 侧的工单提交先支持员工请假。请说明请假天数和请假类型，例如“我要请 3 天年假”。";
        }
        Integer leaveDays = extractLeaveDays(request.getQuestion(), recentMessages);
        String leaveType = extractLeaveType(request.getQuestion(), recentMessages);
        if (leaveDays == null || leaveDays <= 0 || StrUtil.isBlank(leaveType)) {
            return "提交请假工单前，请补充请假天数和请假类型，例如“请 2 天病假”或“请 3 天年假”。";
        }

        Map<String, Object> extData = new LinkedHashMap<>();
        extData.put("leaveDays", leaveDays);
        extData.put("leaveType", leaveType);
        try {
            WorkOrderRecord workOrder = businessCenterClient.createWorkOrder(new CreateWorkOrderCommand(
                    request.getUserId(),
                    "LEAVE",
                    buildRecentUserSummary(recentMessages, request.getQuestion()),
                    extData,
                    "AI_CHAT"
            ));
            return "已为你提交请假工单。工单号：" + safe(workOrder.workOrderNo())
                    + "；请假类型：" + formatLeaveType(leaveType)
                    + "；请假天数：" + leaveDays + " 天"
                    + "；状态：" + formatWorkOrderStatus(workOrder.status()) + "。";
        } catch (BusinessCenterClientException exception) {
            return buildActionFailureReply("提交请假工单", exception);
        }
    }

    private String handleRefundRequest(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String orderNo = resolveLatestMatch(request.getQuestion(), recentMessages, ORDER_NO_PATTERN);
        if (StrUtil.isBlank(orderNo)) {
            return "可以为你提交退款工单。请先提供订单号，例如 ORD202604220001。";
        }
        try {
            WorkOrderRecord workOrder = businessCenterClient.createWorkOrder(new CreateWorkOrderCommand(
                    request.getUserId(),
                    "REFUND",
                    buildRecentUserSummary(recentMessages, request.getQuestion()),
                    Map.of("orderNo", normalizeCode(orderNo)),
                    "AI_CHAT"
            ));
            return "已为你提交退款工单。工单号：" + safe(workOrder.workOrderNo())
                    + "；关联订单号：" + safe(workOrder.relatedOrderNo())
                    + "；状态：" + formatWorkOrderStatus(workOrder.status()) + "。";
        } catch (BusinessCenterClientException exception) {
            return buildActionFailureReply("提交退款工单", exception);
        }
    }

    private String handleHumanHandoff(ChatSendRequest request, List<ChatMessage> recentMessages) {
        try {
            WorkOrderRecord workOrder = businessCenterClient.createWorkOrder(new CreateWorkOrderCommand(
                    request.getUserId(),
                    "HUMAN_SERVICE",
                    buildRecentUserSummary(recentMessages, request.getQuestion()),
                    Map.of(),
                    "AI_CHAT"
            ));
            return "已为你提交转人工工单。工单号：" + safe(workOrder.workOrderNo())
                    + "；状态：" + formatWorkOrderStatus(workOrder.status())
                    + "。后台同事会根据工单继续跟进。";
        } catch (BusinessCenterClientException exception) {
            return buildActionFailureReply("提交转人工工单", exception);
        }
    }

    private String handleStatusQuery(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String workOrderNo = resolveLatestMatch(request.getQuestion(), recentMessages, WORK_ORDER_NO_PATTERN);
        if (StrUtil.isNotBlank(workOrderNo)) {
            try {
                WorkOrderRecord workOrder = businessCenterClient.getWorkOrder(normalizeCode(workOrderNo));
                return buildWorkOrderStatusReply(workOrder);
            } catch (BusinessCenterClientException exception) {
                return buildActionFailureReply("查询工单状态", exception);
            }
        }

        String orderNo = resolveLatestMatch(request.getQuestion(), recentMessages, ORDER_NO_PATTERN);
        if (StrUtil.isNotBlank(orderNo)) {
            try {
                OrderRecord order = businessCenterClient.getOrder(normalizeCode(orderNo));
                return buildOrderStatusReply(order);
            } catch (BusinessCenterClientException exception) {
                return buildActionFailureReply("查询订单状态", exception);
            }
        }
        return "请提供订单号或工单号，我再帮你查询状态。例如 ORD202604220001 或 WO202604220001。";
    }

    private String buildWorkOrderStatusReply(WorkOrderRecord workOrder) {
        StringBuilder builder = new StringBuilder("查询结果：工单号 ")
                .append(safe(workOrder.workOrderNo()))
                .append("，类型：").append(formatWorkOrderType(workOrder.workOrderType()))
                .append("，状态：").append(formatWorkOrderStatus(workOrder.status()));
        if (StrUtil.isNotBlank(workOrder.rejectReason())) {
            builder.append("，驳回原因：").append(workOrder.rejectReason());
        }
        if (StrUtil.isNotBlank(workOrder.processRemark())) {
            builder.append("，处理备注：").append(workOrder.processRemark());
        }
        builder.append("。");
        return builder.toString();
    }

    private String buildOrderStatusReply(OrderRecord order) {
        StringBuilder builder = new StringBuilder("查询结果：订单号 ")
                .append(safe(order.orderNo()))
                .append("，产品：").append(safe(order.productNameSnapshot()))
                .append("，状态：").append(formatOrderStatus(order.status()));
        if (StrUtil.isNotBlank(order.cancelReason())) {
            builder.append("，取消原因：").append(order.cancelReason());
        }
        builder.append("。");
        return builder.toString();
    }

    private String buildActionFailureReply(String actionName, BusinessCenterClientException exception) {
        String message = extractUsefulMessage(exception.getMessage());
        if (StrUtil.isBlank(message)) {
            return actionName + "失败，请稍后重试。";
        }
        return actionName + "失败：" + message;
    }

    private String extractUsefulMessage(String rawMessage) {
        if (StrUtil.isBlank(rawMessage)) {
            return null;
        }
        int messageIndex = rawMessage.indexOf("\"message\":\"");
        if (messageIndex >= 0) {
            int begin = messageIndex + "\"message\":\"".length();
            int end = rawMessage.indexOf("\"", begin);
            if (end > begin) {
                return rawMessage.substring(begin, end);
            }
        }
        return rawMessage;
    }

    private String buildConversationText(ChatSendRequest request, List<ChatMessage> recentMessages) {
        List<String> segments = new ArrayList<>();
        if (recentMessages != null) {
            recentMessages.stream()
                    .filter(Objects::nonNull)
                    .map(ChatMessage::getContent)
                    .filter(StrUtil::isNotBlank)
                    .forEach(segments::add);
        }
        if (StrUtil.isNotBlank(request.getQuestion())) {
            segments.add(request.getQuestion());
        }
        return String.join("\n", segments);
    }

    private String buildRecentUserSummary(List<ChatMessage> recentMessages, String currentQuestion) {
        List<String> userMessages = new ArrayList<>();
        if (recentMessages != null) {
            recentMessages.stream()
                    .filter(Objects::nonNull)
                    .filter(message -> "user".equalsIgnoreCase(message.getRole()))
                    .map(ChatMessage::getContent)
                    .filter(StrUtil::isNotBlank)
                    .forEach(userMessages::add);
        }
        if (StrUtil.isNotBlank(currentQuestion)) {
            userMessages.add(currentQuestion);
        }
        if (userMessages.isEmpty()) {
            return StrUtil.blankToDefault(currentQuestion, "");
        }
        int start = Math.max(0, userMessages.size() - 3);
        return String.join("；", userMessages.subList(start, userMessages.size()));
    }

    private boolean looksLikeLeaveRequest(String text) {
        return StrUtil.containsAnyIgnoreCase(StrUtil.blankToDefault(text, ""),
                "请假", "年假", "病假", "事假", "休假");
    }

    private Integer extractLeaveDays(String currentQuestion, List<ChatMessage> recentMessages) {
        String value = resolveLatestMatch(currentQuestion, recentMessages, LEAVE_DAYS_PATTERN);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractLeaveType(String currentQuestion, List<ChatMessage> recentMessages) {
        for (String text : candidateTexts(currentQuestion, recentMessages)) {
            String normalized = StrUtil.blankToDefault(text, "");
            if (normalized.contains("年假")) {
                return "ANNUAL";
            }
            if (normalized.contains("病假")) {
                return "SICK";
            }
            if (normalized.contains("事假")) {
                return "PERSONAL";
            }
            if (normalized.contains("其他")) {
                return "OTHER";
            }
        }
        return null;
    }

    private Integer resolveOrderQuantity(String currentQuestion, List<ChatMessage> recentMessages) {
        Integer currentQuantity = parseInteger(firstMatch(currentQuestion, QUANTITY_PATTERN));
        if (currentQuantity != null) {
            return currentQuantity;
        }
        if (!isOrderSupplement(currentQuestion)) {
            return null;
        }
        return parseInteger(resolveLatestUserMatch(recentMessages, QUANTITY_PATTERN));
    }

    private String resolveOrderProductNo(String currentQuestion, List<ChatMessage> recentMessages) {
        String currentProductNo = firstMatch(currentQuestion, PRODUCT_NO_PATTERN);
        if (StrUtil.isNotBlank(currentProductNo)) {
            return currentProductNo;
        }
        if (!isOrderSupplement(currentQuestion)) {
            return null;
        }
        return resolveLatestUserMatch(recentMessages, PRODUCT_NO_PATTERN);
    }

    private boolean isOrderSupplement(String question) {
        String normalized = StrUtil.blankToDefault(question, "").replaceAll("\\s+", "");
        if (StrUtil.isBlank(normalized)) {
            return false;
        }
        boolean containsParameter = StrUtil.isNotBlank(firstMatch(normalized, PRODUCT_NO_PATTERN))
                || StrUtil.isNotBlank(firstMatch(normalized, QUANTITY_PATTERN));
        if (!containsParameter) {
            return false;
        }
        return !looksLikeFreshOrderRequest(normalized);
    }

    private boolean looksLikeFreshOrderRequest(String question) {
        return StrUtil.containsAny(question, "下单", "购买", "我要买", "我要下单", "帮我下单", "订购");
    }

    private Integer parseInteger(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String buildMissingOrderFieldsReply(String productNo, Integer quantity) {
        boolean missingProductNo = StrUtil.isBlank(productNo);
        boolean missingQuantity = quantity == null || quantity <= 0;
        if (missingProductNo && missingQuantity) {
            return "可以为你提交订单。请先提供产品号和购买数量，例如“我要买 P-1002 2件”。";
        }
        if (missingProductNo) {
            return "可以为你提交订单。请先提供产品号，例如“我要买 P-1002 2件”。";
        }
        return "可以为你提交订单。请再补充购买数量，例如“我要买 " + normalizeCode(productNo) + " 2件”。";
    }

    private String firstMatch(String text, Pattern pattern) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
    }

    private String resolveLatestMatch(String currentQuestion, List<ChatMessage> recentMessages, Pattern pattern) {
        for (String text : candidateTexts(currentQuestion, recentMessages)) {
            String value = firstMatch(text, pattern);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveLatestUserMatch(List<ChatMessage> recentMessages, Pattern pattern) {
        if (recentMessages == null) {
            return null;
        }
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = recentMessages.get(index);
            if (message == null || !"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            String value = firstMatch(message.getContent(), pattern);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> candidateTexts(String currentQuestion, List<ChatMessage> recentMessages) {
        List<String> texts = new ArrayList<>();
        if (StrUtil.isNotBlank(currentQuestion)) {
            texts.add(currentQuestion);
        }
        if (recentMessages != null) {
            for (int index = recentMessages.size() - 1; index >= 0; index--) {
                ChatMessage message = recentMessages.get(index);
                if (message == null || !"user".equalsIgnoreCase(message.getRole())) {
                    continue;
                }
                if (StrUtil.isNotBlank(message.getContent())) {
                    texts.add(message.getContent());
                }
            }
        }
        return texts;
    }

    private String normalizeCode(String code) {
        return StrUtil.blankToDefault(code, "").trim().toUpperCase();
    }

    private String formatOrderStatus(String status) {
        return switch (StrUtil.blankToDefault(status, "")) {
            case "UNPAID" -> "未付款";
            case "PAID" -> "已付款";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String formatWorkOrderStatus(String status) {
        return switch (StrUtil.blankToDefault(status, "")) {
            case "PENDING" -> "未处理";
            case "PROCESSING" -> "处理中";
            case "RESOLVED" -> "已处理";
            case "REJECTED" -> "不予处理";
            default -> status;
        };
    }

    private String formatWorkOrderType(String type) {
        return switch (StrUtil.blankToDefault(type, "")) {
            case "LEAVE" -> "请假";
            case "REFUND" -> "退款";
            case "HUMAN_SERVICE" -> "转人工";
            default -> type;
        };
    }

    private String formatLeaveType(String type) {
        return switch (StrUtil.blankToDefault(type, "")) {
            case "ANNUAL" -> "年假";
            case "SICK" -> "病假";
            case "PERSONAL" -> "事假";
            case "OTHER" -> "其他";
            default -> type;
        };
    }

    private String safe(String value) {
        return StrUtil.blankToDefault(value, "");
    }

    private String formatPrice(java.math.BigDecimal price) {
        return price == null ? "" : price.stripTrailingZeros().toPlainString();
    }
}
