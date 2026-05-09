package com.airag.businesscenter.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductRequest(@NotBlank String productNo,
                                   @NotBlank String productName,
                                   @NotNull @DecimalMin("0.01") BigDecimal price,
                                   String description) {
}
