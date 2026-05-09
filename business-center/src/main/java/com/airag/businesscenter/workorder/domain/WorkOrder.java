package com.airag.businesscenter.workorder.domain;

import com.airag.businesscenter.user.domain.UserType;

import java.time.LocalDateTime;
import java.util.Map;

public record WorkOrder(Long id,
                        String workOrderNo,
                        Long userId,
                        UserType userType,
                        WorkOrderType workOrderType,
                        WorkOrderStatus status,
                        String title,
                        String content,
                        String relatedOrderNo,
                        Map<String, Object> extData,
                        String rejectReason,
                        Long processedBy,
                        String processRemark,
                        LocalDateTime processedTime,
                        WorkOrderSourceChannel sourceChannel,
                        LocalDateTime createdTime,
                        LocalDateTime updatedTime) {

    public WorkOrder withStatus(WorkOrderStatus nextStatus,
                                Long nextProcessedBy,
                                String nextProcessRemark,
                                String nextRejectReason) {
        LocalDateTime now = LocalDateTime.now();
        return new WorkOrder(
                id,
                workOrderNo,
                userId,
                userType,
                workOrderType,
                nextStatus,
                title,
                content,
                relatedOrderNo,
                extData,
                nextRejectReason,
                nextProcessedBy,
                nextProcessRemark,
                now,
                sourceChannel,
                createdTime,
                now
        );
    }
}
