package com.airag.modules.integration.businesscenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductRecord(Long id,
                            String productNo,
                            String productName,
                            BigDecimal price,
                            String description,
                            LocalDateTime createdTime,
                            LocalDateTime updatedTime) {
}
