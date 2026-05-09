package com.airag.businesscenter.product.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Product(Long id,
                      String productNo,
                      String productName,
                      BigDecimal price,
                      String description,
                      LocalDateTime createdTime,
                      LocalDateTime updatedTime) {
}
