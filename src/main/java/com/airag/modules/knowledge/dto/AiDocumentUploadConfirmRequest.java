package com.airag.modules.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiDocumentUploadConfirmRequest {

    @NotNull(message = "analysisId cannot be null")
    private Long analysisId;

    private Long knowledgeBaseId;

    private String documentName;
}
