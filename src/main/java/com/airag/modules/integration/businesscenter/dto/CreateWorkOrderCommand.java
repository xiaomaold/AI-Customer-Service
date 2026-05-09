package com.airag.modules.integration.businesscenter.dto;

import java.util.Map;

public record CreateWorkOrderCommand(Long userId,
                                     String workOrderType,
                                     String content,
                                     Map<String, Object> extData,
                                     String sourceChannel) {
}
