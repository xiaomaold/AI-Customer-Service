package com.airag.modules.integration.businesscenter.dto;

import java.util.Map;

public record WorkOrderRecord(Long id,
                              String workOrderNo,
                              Long userId,
                              String userType,
                              String workOrderType,
                              String status,
                              String title,
                              String content,
                              String relatedOrderNo,
                              Map<String, Object> extData,
                              String rejectReason,
                              Long processedBy,
                              String processRemark,
                              String processedTime,
                              String sourceChannel,
                              String createdTime,
                              String updatedTime) {
}
