package com.airag.modules.chat.routing;

public record QuestionIntentFeatures(
        boolean generationRequested,
        boolean knowledgeRequired,
        boolean generalConcept,
        boolean documentDiscovery,
        boolean structuredFact,
        boolean casualChat,
        boolean followUpRewrite,
        boolean knowledgeBaseListQuestion,
        boolean phoneQuery,
        boolean emailQuery,
        boolean addressQuery,
        boolean companyIntroQuery,
        EnterpriseNeedLevel enterpriseNeedLevel,
        CustomerServiceDomain serviceDomain,
        CustomerServiceIntent serviceIntent,
        ActionRequestType actionRequestType
) {
}
