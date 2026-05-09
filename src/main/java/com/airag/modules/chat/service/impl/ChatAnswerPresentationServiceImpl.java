package com.airag.modules.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.chat.service.ChatAnswerPresentationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ChatAnswerPresentationServiceImpl implements ChatAnswerPresentationService {

    private static final List<String> ENHANCEMENT_PREFIXES = List.of("FOLLOW_UP_");

    @Override
    public String enrichReferenceContent(String question, String answer, String referenceContent) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "").trim();
        String normalizedAnswer = StrUtil.blankToDefault(answer, "").trim();
        DisplayTopic topic = detectTopic(normalizedQuestion, normalizedAnswer);

        List<String> preservedLines = preserveBaseLines(referenceContent);
        List<String> followUps = shouldShowFollowUps(topic, normalizedQuestion, normalizedAnswer, referenceContent)
                ? buildFollowUps(topic, normalizedQuestion, normalizedAnswer)
                : List.of();

        if (followUps.isEmpty()) {
            return joinLines(preservedLines);
        }

        List<String> lines = new ArrayList<>(preservedLines);
        for (int index = 0; index < followUps.size(); index++) {
            lines.add("FOLLOW_UP_" + (index + 1) + ": " + followUps.get(index));
        }
        return joinLines(lines);
    }

    private List<String> preserveBaseLines(String referenceContent) {
        if (StrUtil.isBlank(referenceContent)) {
            return new ArrayList<>();
        }
        return Arrays.stream(referenceContent.split("\\R"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .filter(line -> ENHANCEMENT_PREFIXES.stream().noneMatch(line::startsWith))
                .toList();
    }

    private boolean shouldShowFollowUps(DisplayTopic topic, String question, String answer, String referenceContent) {
        return topic != DisplayTopic.NONE
                && hasEnterpriseKnowledgeReference(referenceContent)
                && isRuleLike(question, answer);
    }

    private boolean hasEnterpriseKnowledgeReference(String referenceContent) {
        if (StrUtil.isBlank(referenceContent)) {
            return false;
        }
        return Arrays.stream(referenceContent.split("\\R"))
                .map(String::trim)
                .anyMatch(line -> line.matches("^REFERENCE_\\d+:\\s*.+$"));
    }

    private boolean isRuleLike(String question, String answer) {
        String combined = question + "\n" + answer;
        return containsAny(combined, List.of(
                "规则", "制度", "政策", "流程", "规定", "说明", "方式", "时效",
                "材料", "条件", "要求", "联系方式", "邮箱", "电话", "地址"
        ));
    }

    private DisplayTopic detectTopic(String question, String answer) {
        String combined = question + "\n" + answer;
        if (containsAny(combined, List.of("退款", "售后"))) {
            return DisplayTopic.REFUND;
        }
        if (containsAny(combined, List.of("请假", "病假", "事假", "婚假", "年假"))) {
            return DisplayTopic.LEAVE;
        }
        if (combined.contains("报销")) {
            return DisplayTopic.REIMBURSEMENT;
        }
        if (containsAny(combined, List.of("联系方式", "客服电话", "邮箱", "地址", "热线"))) {
            return DisplayTopic.CONTACT;
        }
        return DisplayTopic.NONE;
    }

    private List<String> buildFollowUps(DisplayTopic topic, String question, String answer) {
        LinkedHashSet<String> followUps = new LinkedHashSet<>();

        followUps.addAll(buildAnswerAwareFollowUps(topic, question, answer));
        followUps.addAll(buildTopicFallbackFollowUps(topic));

        return followUps.stream()
                .filter(StrUtil::isNotBlank)
                .limit(3)
                .toList();
    }

    private List<String> buildAnswerAwareFollowUps(DisplayTopic topic, String question, String answer) {
        String combined = question + "\n" + answer;
        List<String> followUps = new ArrayList<>();

        switch (topic) {
            case REFUND -> {
                if (containsAny(combined, List.of("申请方式", "客服电话", "在线工单", "邮件"))) {
                    followUps.add("告诉我退款申请方式");
                }
                if (containsAny(combined, List.of("审核", "到账", "工作日", "时效"))) {
                    followUps.add("告诉我退款审核和到账时效");
                }
                if (containsAny(combined, List.of("规则", "条件", "7天", "全额退款", "优惠券", "发票"))) {
                    followUps.add("帮我总结退款规则");
                }
            }
            case LEAVE -> {
                if (containsAny(combined, List.of("病假", "事假", "婚假", "年假", "产假", "陪产假", "丧假"))) {
                    followUps.add("帮我总结请假类型");
                }
                if (containsAny(combined, List.of("流程", "审批", "直属上级", "人事"))) {
                    followUps.add("告诉我请假审批流程");
                }
                if (containsAny(combined, List.of("材料", "病历", "附件", "证明"))) {
                    followUps.add("提取请假所需材料");
                }
            }
            case REIMBURSEMENT -> {
                if (containsAny(combined, List.of("规则", "标准", "范围"))) {
                    followUps.add("帮我总结报销规则");
                }
                if (containsAny(combined, List.of("材料", "发票", "单据", "附件"))) {
                    followUps.add("提取报销所需材料");
                }
                if (containsAny(combined, List.of("审批", "流程"))) {
                    followUps.add("告诉我报销审批流程");
                }
            }
            case CONTACT -> {
                if (containsAny(combined, List.of("邮箱", "@"))) {
                    followUps.add("列出所有相关邮箱");
                }
                if (containsAny(combined, List.of("电话", "热线", "400"))) {
                    followUps.add("列出所有相关电话");
                }
                if (containsAny(combined, List.of("地址", "位置"))) {
                    followUps.add("告诉我公司地址");
                }
            }
            case NONE -> {
            }
        }

        return followUps;
    }

    private List<String> buildTopicFallbackFollowUps(DisplayTopic topic) {
        return switch (topic) {
            case REFUND -> List.of(
                    "帮我总结退款规则",
                    "告诉我退款申请方式",
                    "告诉我退款审核和到账时效"
            );
            case LEAVE -> List.of(
                    "帮我总结请假规则",
                    "提取请假所需材料",
                    "告诉我请假审批要求"
            );
            case REIMBURSEMENT -> List.of(
                    "帮我总结报销规则",
                    "提取报销所需材料",
                    "告诉我报销审批流程"
            );
            case CONTACT -> List.of(
                    "列出所有相关邮箱",
                    "列出所有相关电话",
                    "告诉我公司地址"
            );
            case NONE -> List.of();
        };
    }

    private boolean containsAny(String text, List<String> candidates) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        return candidates.stream().anyMatch(text::contains);
    }

    private String joinLines(List<String> lines) {
        return lines.stream()
                .filter(StrUtil::isNotBlank)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private enum DisplayTopic {
        NONE,
        REFUND,
        LEAVE,
        REIMBURSEMENT,
        CONTACT
    }
}
