package com.airag.modules.chat.routing.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.chat.routing.BusinessSignalAnalyzer;
import com.airag.modules.chat.routing.BusinessSignalFeatures;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultBusinessSignalAnalyzer implements BusinessSignalAnalyzer {

    @Override
    public BusinessSignalFeatures analyze(String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "").trim().toLowerCase();
        boolean documentUploadAction = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.DOCUMENT_UPLOAD_ACTION_ALIASES);
        boolean documentAnalyzeAction = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.DOCUMENT_ANALYZE_ACTION_ALIASES);
        boolean productQueryAction = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.PRODUCT_QUERY_ACTION_ALIASES);
        boolean orderSubmissionAction = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.ORDER_SUBMISSION_ACTION_ALIASES);
        boolean refundRequestAction = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.REFUND_REQUEST_ACTION_ALIASES);
        boolean phone = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.PHONE_ALIASES);
        boolean email = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.EMAIL_ALIASES);
        boolean address = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.ADDRESS_ALIASES);
        boolean preSales = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.PRE_SALES_ALIASES);
        boolean refund = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.REFUND_ALIASES);
        boolean complaint = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.COMPLAINT_ALIASES);
        boolean contactChannel = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.CONTACT_CHANNEL_ALIASES)
                || phone
                || email
                || address;
        boolean orderPayment = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.ORDER_PAYMENT_ALIASES);
        boolean accountPermission = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.ACCOUNT_PERMISSION_ALIASES);
        boolean hrAdmin = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.HR_ADMIN_ALIASES)
                || isLeaveTopicQuestion(normalizedQuestion);
        boolean humanService = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.HUMAN_SERVICE_ALIASES);
        boolean workOrder = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.WORK_ORDER_ALIASES)
                || isExplicitLeaveWorkOrderAction(normalizedQuestion);
        boolean statusQuery = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.STATUS_QUERY_ALIASES);
        boolean businessProcess = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.BUSINESS_PROCESS_ALIASES)
                || hrAdmin;
        boolean afterSales = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.AFTER_SALES_ALIASES)
                || (businessProcess && !refund && !hrAdmin);
        boolean knowledgeConstraint = containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.KNOWLEDGE_REQUIRED_ALIASES)
                || isBusinessProcessKnowledgeQuery(normalizedQuestion, businessProcess)
                || isShortKnowledgeTopicQuery(normalizedQuestion);
        return new BusinessSignalFeatures(
                knowledgeConstraint,
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.DOCUMENT_TARGET_ALIASES),
                phone || email || address,
                documentUploadAction,
                documentAnalyzeAction,
                productQueryAction,
                orderSubmissionAction,
                refundRequestAction,
                phone,
                email,
                address,
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.COMPANY_SUBJECT_ALIASES),
                containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.COMPANY_SUBJECT_ALIASES)
                        && containsAny(normalizedQuestion, QuestionIntentKeywordCatalog.COMPANY_INTRO_ALIASES),
                businessProcess,
                preSales,
                afterSales,
                refund,
                complaint,
                contactChannel,
                orderPayment,
                accountPermission,
                hrAdmin,
                humanService,
                workOrder,
                statusQuery
        );
    }

    private boolean containsAny(String question, List<String> aliases) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return aliases.stream().anyMatch(question::contains);
    }

    private boolean isShortKnowledgeTopicQuery(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }

        String normalized = StrUtil.removeSuffix(StrUtil.removeSuffix(StrUtil.removeSuffix(question, "？"), "?"), "。").trim();
        if (normalized.length() < 2 || normalized.length() > 12) {
            return false;
        }
        if (containsAny(normalized, QuestionIntentKeywordCatalog.GENERAL_GENERATION_ALIASES)
                || containsAny(normalized, QuestionIntentKeywordCatalog.CHAT_ALIASES)
                || containsAny(normalized, QuestionIntentKeywordCatalog.PHONE_ALIASES)
                || containsAny(normalized, QuestionIntentKeywordCatalog.EMAIL_ALIASES)
                || containsAny(normalized, QuestionIntentKeywordCatalog.ADDRESS_ALIASES)) {
            return false;
        }
        return QuestionIntentKeywordCatalog.KNOWLEDGE_TOPIC_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    private boolean isBusinessProcessKnowledgeQuery(String question, boolean businessProcess) {
        if (!businessProcess || StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("哪些")
                || question.contains("哪几")
                || question.contains("什么类型")
                || question.contains("什么种类")
                || question.contains("包括")
                || question.contains("都有哪些")
                || question.contains("可以")
                || question.contains("能否")
                || question.contains("是否可以");
    }

    private boolean isLeaveTopicQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("假")
                && (question.contains("请") || question.contains("休"))
                && !containsAny(question, QuestionIntentKeywordCatalog.GENERAL_GENERATION_ALIASES);
    }

    private boolean isExplicitLeaveWorkOrderAction(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("我要请假")
                || question.contains("帮我请假")
                || question.contains("申请请假")
                || question.contains("提交请假")
                || question.contains("请假申请");
    }
}
