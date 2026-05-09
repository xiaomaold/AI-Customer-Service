package com.airag.businesscenter.workorder.dto;

import com.airag.businesscenter.workorder.domain.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkOrderStatusRequest(@NotNull WorkOrderStatus status,
                                           Long processedBy,
                                           String processRemark,
                                           String rejectReason) {
}
