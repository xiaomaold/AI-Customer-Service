package com.airag.modules.integration.businesscenter.dto;

public record BusinessCenterApiResponse<T>(boolean success,
                                           String code,
                                           String message,
                                           T data) {
}
