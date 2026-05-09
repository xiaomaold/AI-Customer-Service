package com.airag.businesscenter.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CancelOrderRequest(@NotNull Long userId,
                                 @NotBlank String cancelReason) {
}
