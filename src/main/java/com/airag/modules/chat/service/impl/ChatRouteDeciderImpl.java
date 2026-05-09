package com.airag.modules.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.routing.ActionRequestType;
import com.airag.modules.chat.routing.CustomerServiceDomain;
import com.airag.modules.chat.routing.CustomerServiceIntent;
import com.airag.modules.chat.routing.EnterpriseNeedLevel;
import com.airag.modules.chat.routing.QuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.QuestionIntentFeatures;
import com.airag.modules.chat.service.ChatRouteDecider;
import com.airag.modules.chat.service.ChatRouteDecision;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatRouteDeciderImpl implements ChatRouteDecider {

    private static final List<String> GENERATION_PATTERNS = List.of(
            "\u751f\u6210", "\u5199\u4e00\u4efd", "\u5199\u4e2a", "\u8d77\u8349", "\u62df\u4e00\u4efd", "\u5e2e\u6211\u5199", "\u5e2e\u6211\u751f\u6210", "\u6574\u7406\u6210"
    );

    private static final List<String> FORM_PATTERNS = List.of(
            "\u7533\u8bf7\u8868", "\u7533\u8bf7\u5355", "\u5ba1\u6279\u5355", "\u8868\u5355", "\u6a21\u677f", "\u8303\u6587", "\u7533\u8bf7\u4e66", "\u62a5\u544a", "\u8bf4\u660e", "\u90ae\u4ef6"
    );

    private static final List<String> STRICT_RULE_PATTERNS = List.of(
            "\u89c4\u5219", "\u5236\u5ea6", "\u653f\u7b56", "\u6d41\u7a0b", "\u6b65\u9aa4", "\u8bf4\u660e", "\u6307\u5f15", "\u6761\u4ef6", "\u6750\u6599", "\u65f6\u6548"
    );

    private static final List<String> AMBIGUOUS_BUSINESS_PATTERNS = List.of(
            "\u9000\u6b3e", "\u9000\u8d27", "\u8bf7\u5047", "\u5de5\u5355", "\u8ba2\u5355", "\u4e0b\u5355", "\u4ea7\u54c1", "\u5546\u54c1", "\u4eba\u5de5", "\u5ba2\u670d", "\u552e\u540e"
    );

    private static final List<String> PRODUCT_ACTION_PATTERNS = List.of(
            "\u4ea7\u54c1\u4fe1\u606f", "\u5546\u54c1\u4fe1\u606f", "\u4ea7\u54c1\u8be6\u60c5", "\u5546\u54c1\u8be6\u60c5",
            "\u67e5\u770b\u4ea7\u54c1", "\u67e5\u8be2\u4ea7\u54c1", "\u4ea7\u54c1\u4ecb\u7ecd", "\u4ea7\u54c1\u8d44\u6599"
    );

    private static final List<String> ORDER_ACTION_PATTERNS = List.of(
            "\u4e0b\u5355", "\u63d0\u4ea4\u8ba2\u5355", "\u521b\u5efa\u8ba2\u5355", "\u751f\u6210\u8ba2\u5355", "\u8d2d\u4e70", "\u8ba2\u8d2d", "\u6211\u8981\u4e0b\u5355", "\u5e2e\u6211\u4e0b\u5355"
    );

    private static final List<String> REFUND_ACTION_PATTERNS = List.of(
            "\u6211\u8981\u9000\u6b3e", "\u7533\u8bf7\u9000\u6b3e", "\u63d0\u4ea4\u9000\u6b3e", "\u53d1\u8d77\u9000\u6b3e", "\u5e2e\u6211\u9000\u6b3e", "\u5e2e\u6211\u7533\u8bf7\u9000\u6b3e"
    );

    private static final List<String> HUMAN_ACTION_PATTERNS = List.of(
            "\u8f6c\u4eba\u5de5", "\u4eba\u5de5\u5ba2\u670d", "\u8f6c\u5230\u4eba\u5de5", "\u4eba\u5de5\u5904\u7406", "\u4eba\u5de5\u4ecb\u5165"
    );

    private static final List<String> WORK_ORDER_ACTION_PATTERNS = List.of(
            "\u63d0\u4ea4\u5de5\u5355", "\u53d1\u8d77\u5de5\u5355", "\u521b\u5efa\u5de5\u5355",
            "\u63d0\u4ea4\u8bf7\u5047\u5de5\u5355", "\u7533\u8bf7\u8bf7\u5047", "\u63d0\u4ea4\u8bf7\u5047",
            "\u6211\u8981\u8bf7\u5047", "\u5e2e\u6211\u8bf7\u5047", "\u8bf7\u5047\u7533\u8bf7"
    );

    private static final List<String> STATUS_ACTION_PATTERNS = List.of(
            "\u67e5\u8be2\u72b6\u6001", "\u67e5\u770b\u72b6\u6001", "\u67e5\u8fdb\u5ea6", "\u67e5\u770b\u8fdb\u5ea6", "\u8ba2\u5355\u72b6\u6001", "\u5de5\u5355\u72b6\u6001", "\u9000\u6b3e\u8fdb\u5ea6"
    );

    private static final List<String> ACTION_CLARIFICATION_PATTERNS = List.of(
            "\u600e\u4e48", "\u5982\u4f55", "\u9700\u8981", "\u662f\u5426", "\u80fd\u5426"
    );

    private final QuestionIntentFeatureExtractor featureExtractor;

    public ChatRouteDeciderImpl(QuestionIntentFeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
    }

    @Override
    public ChatRouteDecision decide(ChatSendRequest request, List<ChatMessage> recentMessages) {
        String question = StrUtil.blankToDefault(request.getQuestion(), "").trim();
        if (question.isEmpty()) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.UNIFIED_AGENT,
                    "EMPTY_QUESTION",
                    CustomerServiceDomain.OTHER,
                    CustomerServiceIntent.KNOWLEDGE_QA,
                    ActionRequestType.NONE
            );
        }

        if (StrUtil.isNotBlank(request.getCarryoverDocumentName())) {
            QuestionIntentFeatures features = featureExtractor.extract(question);
            return new ChatRouteDecision(
                    ChatRouteModeEnum.UNIFIED_AGENT,
                    "DOCUMENT_CARRYOVER",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }

        ChatRouteModeEnum continuedMode = extractLastRouteMode(recentMessages);
        QuestionIntentFeatures features = featureExtractor.extract(question);
        EnterpriseNeedLevel enterpriseNeedLevel = features.enterpriseNeedLevel();

        if (shouldContinueGeneralGeneration(continuedMode, question, features)) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.GENERAL_GENERATION,
                    "FOLLOW_UP_CONTINUATION",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }
        if (continuedMode != null && features.followUpRewrite()) {
            return new ChatRouteDecision(
                    continuedMode,
                    "FOLLOW_UP_CONTINUATION",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }
        if (isLowSignalQuestion(question)) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.GENERAL_GENERATION,
                    "INVALID_INPUT",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }
        if (isSelfReferentialQuestion(question)) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.GENERAL_GENERATION,
                    "SELF_REFERENTIAL",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }

        ChatRouteDecision clarificationContinuation = resolveBusinessClarificationContinuation(question, recentMessages);
        if (clarificationContinuation != null) {
            return clarificationContinuation;
        }
        if (shouldAskBusinessClarification(question, features)) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.GENERAL_GENERATION,
                    "BUSINESS_CLARIFICATION",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    ActionRequestType.NONE
            );
        }
        if (shouldTreatActionRequestAsKnowledgeGuidance(question, features)) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.UNIFIED_AGENT,
                    "KNOWLEDGE_REQUIRED",
                    features.serviceDomain(),
                    resolveKnowledgeGuidanceIntent(features),
                    features.actionRequestType()
            );
        }
        if (isDirectActionRequest(question, features.actionRequestType())) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.GENERAL_GENERATION,
                    "ACTION_REQUEST",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }
        if (request.getKnowledgeBaseId() != null) {
            return new ChatRouteDecision(
                    ChatRouteModeEnum.UNIFIED_AGENT,
                    "EXPLICIT_KNOWLEDGE_BASE",
                    features.serviceDomain(),
                    features.serviceIntent(),
                    features.actionRequestType()
            );
        }

        if (enterpriseNeedLevel != EnterpriseNeedLevel.ENTERPRISE_REQUIRED) {
            if (features.generalConcept()) {
                return new ChatRouteDecision(ChatRouteModeEnum.GENERAL_GENERATION, "GENERAL_CONCEPT", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
            }
            if (features.generationRequested()) {
                return new ChatRouteDecision(ChatRouteModeEnum.GENERAL_GENERATION, "GENERATION_REQUESTED", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
            }
            if (features.casualChat()) {
                return new ChatRouteDecision(ChatRouteModeEnum.GENERAL_GENERATION, "CASUAL_CHAT", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
            }
            if (looksLikeDirectGeneralQuestion(question)) {
                return new ChatRouteDecision(ChatRouteModeEnum.GENERAL_GENERATION, "GENERAL_REQUEST", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
            }
            String fallbackReason = enterpriseNeedLevel == EnterpriseNeedLevel.GENERAL_REQUEST
                    ? "GENERAL_REQUEST"
                    : "UNCERTAIN_REQUEST";
            return new ChatRouteDecision(ChatRouteModeEnum.GENERAL_GENERATION, fallbackReason, features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
        }
        if (shouldUseHybridGeneration(question, features)) {
            return new ChatRouteDecision(ChatRouteModeEnum.HYBRID_GENERATION, "KNOWLEDGE_AND_GENERATION", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
        }
        if (features.documentDiscovery()) {
            return new ChatRouteDecision(ChatRouteModeEnum.UNIFIED_AGENT, "DOCUMENT_DISCOVERY", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
        }
        if (features.structuredFact()) {
            return new ChatRouteDecision(ChatRouteModeEnum.UNIFIED_AGENT, "STRUCTURED_FACT_QUERY", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
        }
        if (features.knowledgeRequired()) {
            return new ChatRouteDecision(ChatRouteModeEnum.UNIFIED_AGENT, "KNOWLEDGE_REQUIRED", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
        }
        return new ChatRouteDecision(ChatRouteModeEnum.UNIFIED_AGENT, "DEFAULT_AGENT", features.serviceDomain(), features.serviceIntent(), features.actionRequestType());
    }

    private boolean isLowSignalQuestion(String question) {
        String compact = StrUtil.blankToDefault(question, "").replaceAll("\\s+", "");
        String alphanumeric = compact.replaceAll("[\\p{Punct}，。！？；‘’“”、《》\\s]+", "");
        if (StrUtil.isBlank(alphanumeric)) {
            return true;
        }
        if (alphanumeric.length() <= 6 && alphanumeric.matches("[0-9]+")) {
            return true;
        }
        if (alphanumeric.length() <= 4 && alphanumeric.matches("[a-zA-Z]+")) {
            return true;
        }
        return compact.matches("[\\p{Punct}，。！？；‘’“”、《》\\s]+");
    }

    private boolean shouldContinueGeneralGeneration(ChatRouteModeEnum continuedMode,
                                                    String question,
                                                    QuestionIntentFeatures features) {
        return continuedMode == ChatRouteModeEnum.GENERAL_GENERATION
                && (features.followUpRewrite() || isShortFollowUpQuestion(question));
    }

    private boolean isShortFollowUpQuestion(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim().toLowerCase();
        if (StrUtil.isBlank(normalized) || normalized.length() > 18) {
            return false;
        }
        return normalized.contains("\u5177\u4f53")
                || normalized.contains("\u8be6\u7ec6")
                || normalized.contains("\u7ec6\u8bf4")
                || normalized.contains("\u5c55\u5f00")
                || normalized.contains("\u8865\u5145")
                || normalized.contains("\u591a\u8bf4")
                || normalized.contains("\u518d\u8bf4")
                || normalized.contains("\u8bf4\u4e00\u4e0b")
                || normalized.contains("\u8bf4\u660e\u767d")
                || normalized.contains("\u5177\u4f53\u4f4d\u7f6e");
    }

    private boolean isDirectActionRequest(String question, ActionRequestType actionRequestType) {
        return isDocumentActionRequest(actionRequestType)
                || (isBusinessGuidanceAction(actionRequestType) && hasExplicitActionCue(question, actionRequestType));
    }

    private boolean shouldTreatActionRequestAsKnowledgeGuidance(String question, QuestionIntentFeatures features) {
        return features != null
                && isBusinessGuidanceAction(features.actionRequestType())
                && hasExplicitRuleCue(question);
    }

    private boolean shouldAskBusinessClarification(String question, QuestionIntentFeatures features) {
        if (features == null) {
            return false;
        }
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (StrUtil.isBlank(normalized) || features.structuredFact()) {
            return false;
        }
        if (hasExplicitRuleCue(normalized) || hasExplicitActionCue(normalized, features.actionRequestType())) {
            return false;
        }
        return containsAny(normalized, AMBIGUOUS_BUSINESS_PATTERNS);
    }

    private ChatRouteDecision resolveBusinessClarificationContinuation(String question, List<ChatMessage> recentMessages) {
        if (!"BUSINESS_CLARIFICATION".equals(extractLastRouteReason(recentMessages))) {
            return null;
        }
        String previousUserQuestion = extractLastUserQuestion(recentMessages);
        if (StrUtil.isBlank(previousUserQuestion)) {
            return null;
        }
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        if (isGenericKnowledgeChoice(normalized) || containsAny(normalized, STRICT_RULE_PATTERNS)) {
            return buildClarificationKnowledgeDecision(previousUserQuestion);
        }
        if (normalized.contains("\u7533\u8bf7\u9000\u6b3e") || normalized.contains("\u63d0\u4ea4\u9000\u6b3e") || normalized.contains("\u53d1\u8d77\u9000\u6b3e")) {
            return buildClarificationActionDecision(previousUserQuestion, ActionRequestType.REFUND_REQUEST, CustomerServiceIntent.WORK_ORDER_SUBMISSION);
        }
        if (normalized.contains("\u7533\u8bf7\u8bf7\u5047") || normalized.contains("\u63d0\u4ea4\u8bf7\u5047") || normalized.contains("\u8bf7\u5047\u5de5\u5355")) {
            return buildClarificationActionDecision(previousUserQuestion, ActionRequestType.WORK_ORDER_SUBMISSION, CustomerServiceIntent.WORK_ORDER_SUBMISSION);
        }
        if (normalized.contains("\u8f6c\u4eba\u5de5")) {
            return buildClarificationActionDecision(previousUserQuestion, ActionRequestType.HUMAN_HANDOFF, CustomerServiceIntent.HUMAN_HANDOFF);
        }
        if (normalized.contains("\u63d0\u4ea4\u8ba2\u5355") || normalized.contains("\u5e2e\u6211\u4e0b\u5355") || normalized.contains("\u6211\u8981\u4e0b\u5355")) {
            return buildClarificationActionDecision(previousUserQuestion, ActionRequestType.ORDER_SUBMISSION, CustomerServiceIntent.ORDER_SUBMISSION);
        }
        if (normalized.contains("\u67e5\u4ea7\u54c1") || normalized.contains("\u4ea7\u54c1\u4fe1\u606f") || normalized.contains("\u5546\u54c1\u4fe1\u606f")) {
            return buildClarificationActionDecision(previousUserQuestion, ActionRequestType.PRODUCT_QUERY, CustomerServiceIntent.PRODUCT_QUERY);
        }
        if (isGenericActionChoice(normalized)) {
            ActionRequestType actionRequestType = inferClarificationActionType(previousUserQuestion);
            if (actionRequestType != ActionRequestType.NONE) {
                return buildClarificationActionDecision(
                        previousUserQuestion,
                        actionRequestType,
                        inferClarificationIntent(actionRequestType)
                );
            }
        }
        return null;
    }

    private ChatRouteDecision buildClarificationKnowledgeDecision(String previousUserQuestion) {
        QuestionIntentFeatures previousFeatures = featureExtractor.extract(previousUserQuestion);
        return new ChatRouteDecision(
                ChatRouteModeEnum.UNIFIED_AGENT,
                "KNOWLEDGE_REQUIRED",
                previousFeatures.serviceDomain(),
                CustomerServiceIntent.PROCESS_QUERY,
                ActionRequestType.NONE
        );
    }

    private ChatRouteDecision buildClarificationActionDecision(String previousUserQuestion,
                                                               ActionRequestType actionRequestType,
                                                               CustomerServiceIntent serviceIntent) {
        QuestionIntentFeatures previousFeatures = featureExtractor.extract(previousUserQuestion);
        return new ChatRouteDecision(
                ChatRouteModeEnum.GENERAL_GENERATION,
                "ACTION_REQUEST",
                previousFeatures.serviceDomain(),
                serviceIntent,
                actionRequestType
        );
    }

    private boolean isGenericKnowledgeChoice(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        return normalized.contains("\u67e5\u89c4\u5219")
                || normalized.contains("\u770b\u89c4\u5219")
                || normalized.contains("\u67e5\u5236\u5ea6")
                || normalized.contains("\u770b\u5236\u5ea6")
                || normalized.contains("\u67e5\u6d41\u7a0b")
                || normalized.contains("\u770b\u6d41\u7a0b")
                || normalized.contains("\u67e5\u8bf4\u660e")
                || normalized.contains("\u770b\u8bf4\u660e")
                || normalized.contains("\u67e5\u653f\u7b56")
                || normalized.contains("\u770b\u653f\u7b56")
                || "\u89c4\u5219".equals(normalized)
                || "\u5236\u5ea6".equals(normalized)
                || "\u6d41\u7a0b".equals(normalized)
                || "\u8bf4\u660e".equals(normalized)
                || "\u653f\u7b56".equals(normalized);
    }

    private boolean isGenericActionChoice(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        return normalized.contains("\u529e\u7406")
                || normalized.contains("\u53bb\u529e")
                || normalized.contains("\u63d0\u4ea4")
                || normalized.contains("\u7533\u8bf7")
                || normalized.contains("\u64cd\u4f5c")
                || normalized.contains("\u5f00\u59cb\u529e")
                || normalized.contains("\u53d1\u8d77")
                || normalized.contains("\u73b0\u5728\u529e")
                || "\u529e\u7406".equals(normalized)
                || "\u63d0\u4ea4".equals(normalized)
                || "\u7533\u8bf7".equals(normalized)
                || "\u5f00\u59cb".equals(normalized);
    }

    private ActionRequestType inferClarificationActionType(String previousUserQuestion) {
        String normalized = StrUtil.blankToDefault(previousUserQuestion, "").trim();
        if (StrUtil.isBlank(normalized)) {
            return ActionRequestType.NONE;
        }
        if (normalized.contains("\u9000\u6b3e") || normalized.contains("\u9000\u8d27")) {
            return ActionRequestType.REFUND_REQUEST;
        }
        if (normalized.contains("\u8bf7\u5047") || normalized.contains("\u5de5\u5355")) {
            return ActionRequestType.WORK_ORDER_SUBMISSION;
        }
        if (normalized.contains("\u4eba\u5de5") || normalized.contains("\u5ba2\u670d")) {
            return ActionRequestType.HUMAN_HANDOFF;
        }
        if (normalized.contains("\u8ba2\u5355") || normalized.contains("\u4e0b\u5355")) {
            return ActionRequestType.ORDER_SUBMISSION;
        }
        if (normalized.contains("\u4ea7\u54c1") || normalized.contains("\u5546\u54c1")) {
            return ActionRequestType.PRODUCT_QUERY;
        }
        return ActionRequestType.NONE;
    }

    private CustomerServiceIntent inferClarificationIntent(ActionRequestType actionRequestType) {
        return switch (actionRequestType) {
            case PRODUCT_QUERY -> CustomerServiceIntent.PRODUCT_QUERY;
            case ORDER_SUBMISSION -> CustomerServiceIntent.ORDER_SUBMISSION;
            case HUMAN_HANDOFF -> CustomerServiceIntent.HUMAN_HANDOFF;
            case WORK_ORDER_SUBMISSION, REFUND_REQUEST -> CustomerServiceIntent.WORK_ORDER_SUBMISSION;
            default -> CustomerServiceIntent.KNOWLEDGE_QA;
        };
    }

    private boolean hasExplicitRuleCue(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        return StrUtil.isNotBlank(normalized) && containsAny(normalized, STRICT_RULE_PATTERNS);
    }

    private boolean hasExplicitActionCue(String question, ActionRequestType actionRequestType) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (StrUtil.isBlank(normalized)) {
            return false;
        }
        return switch (actionRequestType) {
            case PRODUCT_QUERY -> containsAny(normalized, PRODUCT_ACTION_PATTERNS);
            case ORDER_SUBMISSION -> containsAny(normalized, ORDER_ACTION_PATTERNS);
            case REFUND_REQUEST -> containsAny(normalized, REFUND_ACTION_PATTERNS)
                    && !containsAny(normalized, ACTION_CLARIFICATION_PATTERNS);
            case HUMAN_HANDOFF -> containsAny(normalized, HUMAN_ACTION_PATTERNS);
            case WORK_ORDER_SUBMISSION -> containsAny(normalized, WORK_ORDER_ACTION_PATTERNS);
            case STATUS_QUERY -> containsAny(normalized, STATUS_ACTION_PATTERNS);
            default -> false;
        };
    }

    private CustomerServiceIntent resolveKnowledgeGuidanceIntent(QuestionIntentFeatures features) {
        if (features == null) {
            return CustomerServiceIntent.KNOWLEDGE_QA;
        }
        return switch (features.actionRequestType()) {
            case PRODUCT_QUERY -> CustomerServiceIntent.PRODUCT_QUERY;
            case ORDER_SUBMISSION -> CustomerServiceIntent.PROCESS_QUERY;
            case STATUS_QUERY -> CustomerServiceIntent.STATUS_QUERY;
            case HUMAN_HANDOFF, WORK_ORDER_SUBMISSION, REFUND_REQUEST -> CustomerServiceIntent.PROCESS_QUERY;
            default -> features.serviceIntent();
        };
    }

    private boolean isDocumentActionRequest(ActionRequestType actionRequestType) {
        return actionRequestType == ActionRequestType.DOCUMENT_UPLOAD
                || actionRequestType == ActionRequestType.DOCUMENT_ANALYZE;
    }

    private boolean isBusinessGuidanceAction(ActionRequestType actionRequestType) {
        return actionRequestType == ActionRequestType.REFUND_REQUEST
                || actionRequestType == ActionRequestType.PRODUCT_QUERY
                || actionRequestType == ActionRequestType.ORDER_SUBMISSION
                || actionRequestType == ActionRequestType.HUMAN_HANDOFF
                || actionRequestType == ActionRequestType.WORK_ORDER_SUBMISSION
                || actionRequestType == ActionRequestType.STATUS_QUERY;
    }

    private boolean containsAny(String question, List<String> patterns) {
        if (StrUtil.isBlank(question) || patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(question::contains);
    }

    private boolean shouldUseHybridGeneration(String question, QuestionIntentFeatures features) {
        if (features == null || !features.knowledgeRequired()) {
            return false;
        }
        if (features.generationRequested()) {
            return true;
        }
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (StrUtil.isBlank(normalized)) {
            return false;
        }
        return containsAny(normalized, GENERATION_PATTERNS) && containsAny(normalized, FORM_PATTERNS);
    }

    private boolean isSelfReferentialQuestion(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        return "\u6211\u662f\u8c01".equals(normalized)
                || "\u4f60\u662f\u8c01".equals(normalized)
                || "\u6211\u662f\u4ec0\u4e48".equals(normalized)
                || "\u4f60\u662f\u4ec0\u4e48".equals(normalized)
                || "\u4f60\u80fd\u505a\u4ec0\u4e48".equals(normalized)
                || "\u4f60\u4f1a\u4ec0\u4e48".equals(normalized)
                || "\u6211\u521a\u521a\u95ee\u4e86\u4ec0\u4e48".equals(normalized);
    }

    private boolean looksLikeDirectGeneralQuestion(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (normalized.length() < 3) {
            return false;
        }
        if (normalized.contains("\u600e\u4e48")
                || normalized.contains("\u5982\u4f55")
                || normalized.contains("\u4e3a\u4ec0\u4e48")
                || normalized.contains("\u51e0\u53f7")
                || normalized.contains("\u51e0\u5929")
                || normalized.contains("\u51e0\u70b9")
                || normalized.contains("\u591a\u5c11")
                || normalized.contains("\u80fd\u4e0d\u80fd")
                || normalized.contains("\u53ef\u4e0d\u53ef\u4ee5")
                || normalized.contains("\u53ef\u4ee5\u5417")
                || normalized.contains("\u662f\u4ec0\u4e48")
                || normalized.contains("\u600e\u4e48\u6837")
                || normalized.endsWith("\u5417")
                || normalized.endsWith("\u5462")
                || normalized.endsWith("?")
                || normalized.endsWith("\uff1f")) {
            return true;
        }
        return normalized.length() >= 4 && normalized.matches(".*[\\u4e00-\\u9fa5]{2,}.*");
    }

    private ChatRouteModeEnum extractLastRouteMode(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = recentMessages.get(index);
            if (message == null || !"assistant".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            return extractRouteMode(message.getReferenceContent());
        }
        return null;
    }

    private String extractLastRouteReason(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = recentMessages.get(index);
            if (message == null || !"assistant".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            return extractReferenceValue(message.getReferenceContent(), "ROUTE_REASON:");
        }
        return null;
    }

    private String extractLastUserQuestion(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = recentMessages.get(index);
            if (message == null || !"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            if (StrUtil.isNotBlank(message.getContent())) {
                return message.getContent();
            }
        }
        return null;
    }

    private ChatRouteModeEnum extractRouteMode(String referenceContent) {
        String value = extractReferenceValue(referenceContent, "ROUTE_MODE:");
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return ChatRouteModeEnum.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String extractReferenceValue(String referenceContent, String prefix) {
        if (StrUtil.isBlank(referenceContent)) {
            return null;
        }
        for (String line : referenceContent.split("\\R")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            return line.substring(prefix.length()).trim();
        }
        return null;
    }
}
