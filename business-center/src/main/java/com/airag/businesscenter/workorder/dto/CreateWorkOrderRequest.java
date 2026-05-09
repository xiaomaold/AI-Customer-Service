package com.airag.businesscenter.workorder.dto;

import com.airag.businesscenter.workorder.domain.WorkOrderSourceChannel;
import com.airag.businesscenter.workorder.domain.WorkOrderType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateWorkOrderRequest(@NotNull Long userId,
                                     @NotNull WorkOrderType workOrderType,
                                     String content,
                                     Map<String, Object> extData,
                                     WorkOrderSourceChannel sourceChannel) {
}
