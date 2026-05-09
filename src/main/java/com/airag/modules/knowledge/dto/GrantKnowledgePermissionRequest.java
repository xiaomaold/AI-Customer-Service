package com.airag.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GrantKnowledgePermissionRequest {

    @NotNull(message = "userId cannot be null")
    private Long userId;

    @NotBlank(message = "permissionType cannot be blank")
    private String permissionType;
}
