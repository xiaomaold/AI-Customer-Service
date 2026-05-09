package com.airag.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSendRequest {

    private Long userId;

    @NotNull(message = "sessionId cannot be null")
    private Long sessionId;

    private Long knowledgeBaseId;

    @NotBlank(message = "question cannot be blank")
    private String question;

    private String routeMode;

    private String routeReason;

    private String routeDomain;

    private String routeIntent;

    private String routeAction;

    private String executionProfile;

    private String executionDirective;

    private String executionTraceId;

    private String carryoverKnowledgeBaseName;

    private String carryoverDocumentName;
}
