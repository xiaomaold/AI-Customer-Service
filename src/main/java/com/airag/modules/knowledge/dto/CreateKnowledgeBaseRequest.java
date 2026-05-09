package com.airag.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "knowledgeBaseName cannot be blank")
    private String knowledgeBaseName;

    private String description;
}
