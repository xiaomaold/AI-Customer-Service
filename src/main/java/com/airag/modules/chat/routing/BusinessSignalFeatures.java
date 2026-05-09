package com.airag.modules.chat.routing;

public record BusinessSignalFeatures(
        boolean knowledgeConstraint,
        boolean documentTarget,
        boolean structuredFact,
        boolean documentUploadAction,
        boolean documentAnalyzeAction,
        boolean productQueryAction,
        boolean orderSubmissionAction,
        boolean refundRequestAction,
        boolean phone,
        boolean email,
        boolean address,
        boolean companySubject,
        boolean companyIntro,
        boolean businessProcess,
        boolean preSales,
        boolean afterSales,
        boolean refund,
        boolean complaint,
        boolean contactChannel,
        boolean orderPayment,
        boolean accountPermission,
        boolean hrAdmin,
        boolean humanService,
        boolean workOrder,
        boolean statusQuery
) {
}
