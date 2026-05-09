package com.airag.modules.integration.businesscenter.dto;

import java.math.BigDecimal;

public record OrderRecord(Long id,
                          String orderNo,
                          Long userId,
                          String userType,
                          Long productId,
                          String productNo,
                          String productNameSnapshot,
                          BigDecimal unitPriceSnapshot,
                          Integer quantity,
                          BigDecimal totalAmount,
                          String status,
                          String cancelReason,
                          String sourceChannel,
                          String createdTime,
                          String updatedTime) {
}
