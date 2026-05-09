package com.airag.modules.integration.businesscenter.dto;

public record CreateOrderCommand(Long userId,
                                 String productNo,
                                 Integer quantity,
                                 String sourceChannel) {
}
