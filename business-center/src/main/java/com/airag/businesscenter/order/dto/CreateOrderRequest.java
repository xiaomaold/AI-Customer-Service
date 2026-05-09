package com.airag.businesscenter.order.dto;

import com.airag.businesscenter.order.domain.OrderSourceChannel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(@NotNull Long userId,
                                 @NotBlank String productNo,
                                 @Min(1) Integer quantity,
                                 OrderSourceChannel sourceChannel) {
}
