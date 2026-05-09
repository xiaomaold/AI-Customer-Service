package com.airag.modules.chat.routing.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.chat.routing.BusinessSignalFeatures;
import com.airag.modules.chat.routing.EnterpriseNeedClassifier;
import com.airag.modules.chat.routing.EnterpriseNeedLevel;
import com.airag.modules.chat.routing.QuestionIntentKeywordCatalog;
import com.airag.modules.chat.routing.SentencePatternFeatures;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultEnterpriseNeedClassifier implements EnterpriseNeedClassifier {

    private static final List<String> SELF_REFERENTIAL_PATTERNS = List.of(
            "我是谁", "你是谁", "我是什么", "你是什么", "你能做什么", "你会什么", "我刚刚问了什么"
    );

    @Override
    public EnterpriseNeedLevel classify(String question,
                                        SentencePatternFeatures sentencePatterns,
                                        BusinessSignalFeatures businessSignals) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "").trim().toLowerCase();
        if (normalizedQuestion.isEmpty()) {
            return EnterpriseNeedLevel.UNCERTAIN;
        }

        if (isLowSignalQuestion(normalizedQuestion)) {
            return EnterpriseNeedLevel.GENERAL_REQUEST;
        }

        if (isEnterpriseRequired(sentencePatterns, businessSignals, normalizedQuestion)) {
            return EnterpriseNeedLevel.ENTERPRISE_REQUIRED;
        }

        if (isClearlyGeneralRequest(sentencePatterns, businessSignals, normalizedQuestion)) {
            return EnterpriseNeedLevel.GENERAL_REQUEST;
        }

        return EnterpriseNeedLevel.UNCERTAIN;
    }

    private boolean isEnterpriseRequired(SentencePatternFeatures sentencePatterns,
                                         BusinessSignalFeatures businessSignals,
                                         String question) {
        return sentencePatterns.asksKnowledgeBaseList()
                || businessSignals.knowledgeConstraint()
                || businessSignals.documentTarget()
                || businessSignals.structuredFact()
                || businessSignals.businessProcess()
                || businessSignals.preSales()
                || businessSignals.afterSales()
                || businessSignals.refund()
                || businessSignals.complaint()
                || businessSignals.contactChannel()
                || businessSignals.orderPayment()
                || businessSignals.accountPermission()
                || businessSignals.hrAdmin()
                || businessSignals.humanService()
                || businessSignals.workOrder()
                || businessSignals.statusQuery()
                || businessSignals.companySubject()
                || containsAny(question, QuestionIntentKeywordCatalog.KNOWLEDGE_REQUIRED_ALIASES);
    }

    private boolean isClearlyGeneralRequest(SentencePatternFeatures sentencePatterns,
                                            BusinessSignalFeatures businessSignals,
                                            String question) {
        if (containsAny(question, SELF_REFERENTIAL_PATTERNS)) {
            return true;
        }
        if (sentencePatterns.casualChat() || sentencePatterns.followUpRewrite()) {
            return true;
        }
        if ((sentencePatterns.asksDefinition()
                || sentencePatterns.asksPrinciple()
                || sentencePatterns.asksComparison()
                || sentencePatterns.asksReason())
                && !hasEnterpriseSignals(businessSignals)) {
            return true;
        }
        return sentencePatterns.asksGeneration() && !hasEnterpriseSignals(businessSignals);
    }

    private boolean hasEnterpriseSignals(BusinessSignalFeatures businessSignals) {
        return businessSignals.knowledgeConstraint()
                || businessSignals.documentTarget()
                || businessSignals.structuredFact()
                || businessSignals.businessProcess()
                || businessSignals.preSales()
                || businessSignals.afterSales()
                || businessSignals.refund()
                || businessSignals.complaint()
                || businessSignals.contactChannel()
                || businessSignals.orderPayment()
                || businessSignals.accountPermission()
                || businessSignals.hrAdmin()
                || businessSignals.humanService()
                || businessSignals.workOrder()
                || businessSignals.statusQuery()
                || businessSignals.companySubject();
    }

    private boolean isLowSignalQuestion(String question) {
        String compact = question.replaceAll("\\s+", "");
        String alphanumeric = compact.replaceAll("[\\p{Punct}？?！!。.,，、]+", "");
        if (StrUtil.isBlank(alphanumeric)) {
            return true;
        }
        if (alphanumeric.length() <= 6 && alphanumeric.matches("[0-9]+")) {
            return true;
        }
        if (alphanumeric.length() <= 4 && alphanumeric.matches("[a-z]+")) {
            return true;
        }
        return compact.matches("[\\p{Punct}？?！!。.,，、]+");
    }

    private boolean containsAny(String question, List<String> patterns) {
        return patterns.stream().anyMatch(question::contains);
    }
}
