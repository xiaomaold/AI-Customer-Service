package com.airag.modules.chat.routing.impl;

import com.airag.modules.chat.routing.ActionRequestType;
import com.airag.modules.chat.routing.BusinessSignalFeatures;
import com.airag.modules.chat.routing.CustomerServiceDomain;
import com.airag.modules.chat.routing.CustomerServiceIntent;
import com.airag.modules.chat.routing.EnterpriseNeedLevel;
import com.airag.modules.chat.routing.QuestionIntentFeatures;
import com.airag.modules.chat.routing.RouteRuleEngine;
import com.airag.modules.chat.routing.SentencePatternFeatures;
import org.springframework.stereotype.Service;

@Service
public class DefaultRouteRuleEngine implements RouteRuleEngine {

    @Override
    public QuestionIntentFeatures compose(SentencePatternFeatures sentencePatterns,
                                          BusinessSignalFeatures businessSignals,
                                          EnterpriseNeedLevel enterpriseNeedLevel) {
        boolean generationRequested = sentencePatterns.asksGeneration();
        boolean businessProcessGeneration = generationRequested && businessSignals.businessProcess();
        boolean enterpriseRequired = enterpriseNeedLevel == EnterpriseNeedLevel.ENTERPRISE_REQUIRED;
        boolean knowledgeRequired = enterpriseRequired && (businessSignals.knowledgeConstraint() || businessProcessGeneration);
        boolean structuredFact = enterpriseRequired && businessSignals.structuredFact();
        boolean documentDiscovery = enterpriseRequired && sentencePatterns.asksDocumentDiscovery() && businessSignals.documentTarget();
        boolean generalConcept = (sentencePatterns.asksDefinition()
                || sentencePatterns.asksPrinciple()
                || sentencePatterns.asksComparison()
                || sentencePatterns.asksReason())
                && !knowledgeRequired
                && !generationRequested
                && !structuredFact;
        boolean casualChat = sentencePatterns.casualChat() && !knowledgeRequired && !structuredFact;
        CustomerServiceDomain serviceDomain = resolveDomain(businessSignals);
        CustomerServiceIntent serviceIntent = resolveIntent(sentencePatterns, businessSignals, generationRequested, knowledgeRequired, structuredFact, documentDiscovery, casualChat);
        ActionRequestType actionRequestType = resolveActionRequestType(businessSignals);

        return new QuestionIntentFeatures(
                generationRequested,
                knowledgeRequired,
                generalConcept,
                documentDiscovery,
                structuredFact,
                casualChat,
                sentencePatterns.followUpRewrite(),
                sentencePatterns.asksKnowledgeBaseList(),
                businessSignals.phone(),
                businessSignals.email(),
                businessSignals.address(),
                businessSignals.companyIntro(),
                enterpriseNeedLevel,
                serviceDomain,
                serviceIntent,
                actionRequestType
        );
    }

    private CustomerServiceDomain resolveDomain(BusinessSignalFeatures businessSignals) {
        if (businessSignals.complaint()) {
            return CustomerServiceDomain.COMPLAINT_AND_ESCALATION;
        }
        if (businessSignals.refund()) {
            return CustomerServiceDomain.REFUND_AND_RETURNS;
        }
        if (businessSignals.contactChannel() || businessSignals.structuredFact()) {
            return CustomerServiceDomain.CONTACT_AND_CHANNEL;
        }
        if (businessSignals.orderPayment()) {
            return CustomerServiceDomain.ORDER_AND_PAYMENT;
        }
        if (businessSignals.accountPermission()) {
            return CustomerServiceDomain.ACCOUNT_AND_PERMISSION;
        }
        if (businessSignals.hrAdmin()) {
            return CustomerServiceDomain.HR_AND_ADMIN;
        }
        if (businessSignals.preSales()) {
            return CustomerServiceDomain.PRE_SALES;
        }
        if (businessSignals.afterSales() || businessSignals.businessProcess()) {
            return CustomerServiceDomain.AFTER_SALES;
        }
        return CustomerServiceDomain.OTHER;
    }

    private ActionRequestType resolveActionRequestType(BusinessSignalFeatures businessSignals) {
        if (businessSignals.documentUploadAction()) {
            return ActionRequestType.DOCUMENT_UPLOAD;
        }
        if (businessSignals.documentAnalyzeAction()) {
            return ActionRequestType.DOCUMENT_ANALYZE;
        }
        if (businessSignals.productQueryAction()) {
            return ActionRequestType.PRODUCT_QUERY;
        }
        if (businessSignals.orderSubmissionAction()) {
            return ActionRequestType.ORDER_SUBMISSION;
        }
        if (businessSignals.refundRequestAction()) {
            return ActionRequestType.REFUND_REQUEST;
        }
        if (businessSignals.humanService()) {
            return ActionRequestType.HUMAN_HANDOFF;
        }
        if (businessSignals.workOrder()) {
            return ActionRequestType.WORK_ORDER_SUBMISSION;
        }
        if (businessSignals.statusQuery()) {
            return ActionRequestType.STATUS_QUERY;
        }
        return ActionRequestType.NONE;
    }

    private CustomerServiceIntent resolveIntent(SentencePatternFeatures sentencePatterns,
                                                BusinessSignalFeatures businessSignals,
                                                boolean generationRequested,
                                                boolean knowledgeRequired,
                                                boolean structuredFact,
                                                boolean documentDiscovery,
                                                boolean casualChat) {
        if (businessSignals.humanService()) {
            return CustomerServiceIntent.HUMAN_HANDOFF;
        }
        if (businessSignals.complaint()) {
            return CustomerServiceIntent.COMPLAINT_ESCALATION;
        }
        if (documentDiscovery) {
            return CustomerServiceIntent.DOCUMENT_DISCOVERY;
        }
        if (structuredFact) {
            return CustomerServiceIntent.STRUCTURED_FACT_QUERY;
        }
        if (generationRequested && businessSignals.businessProcess()) {
            return CustomerServiceIntent.FORM_GENERATION;
        }
        if (businessSignals.productQueryAction()) {
            return CustomerServiceIntent.PRODUCT_QUERY;
        }
        if (businessSignals.orderSubmissionAction()) {
            return CustomerServiceIntent.ORDER_SUBMISSION;
        }
        if (businessSignals.refundRequestAction()) {
            return CustomerServiceIntent.WORK_ORDER_SUBMISSION;
        }
        if (businessSignals.workOrder()) {
            return CustomerServiceIntent.WORK_ORDER_SUBMISSION;
        }
        if (businessSignals.statusQuery()) {
            return CustomerServiceIntent.STATUS_QUERY;
        }
        if (knowledgeRequired && businessSignals.businessProcess()) {
            return CustomerServiceIntent.PROCESS_QUERY;
        }
        if (knowledgeRequired) {
            return CustomerServiceIntent.RULE_QUERY;
        }
        if (casualChat) {
            return CustomerServiceIntent.SMALL_TALK;
        }
        if (sentencePatterns.asksDefinition()
                || sentencePatterns.asksPrinciple()
                || sentencePatterns.asksComparison()
                || sentencePatterns.asksReason()) {
            return CustomerServiceIntent.KNOWLEDGE_QA;
        }
        if (generationRequested) {
            return CustomerServiceIntent.FORM_GENERATION;
        }
        return CustomerServiceIntent.KNOWLEDGE_QA;
    }
}
