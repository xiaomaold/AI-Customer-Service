package com.airag.businesscenter.order.domain;

import com.airag.businesscenter.user.domain.UserType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(Long id,
                    String orderNo,
                    Long userId,
                    UserType userType,
                    Long productId,
                    String productNo,
                    String productNameSnapshot,
                    BigDecimal unitPriceSnapshot,
                    Integer quantity,
                    BigDecimal totalAmount,
                    OrderStatus status,
                    String cancelReason,
                    OrderSourceChannel sourceChannel,
                    LocalDateTime createdTime,
                    LocalDateTime updatedTime) {

    public Order withStatus(OrderStatus nextStatus, String nextCancelReason) {
        return new Order(
                id,
                orderNo,
                userId,
                userType,
                productId,
                productNo,
                productNameSnapshot,
                unitPriceSnapshot,
                quantity,
                totalAmount,
                nextStatus,
                nextCancelReason,
                sourceChannel,
                createdTime,
                LocalDateTime.now()
        );
    }
}
