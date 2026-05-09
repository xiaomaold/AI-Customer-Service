package com.airag.modules.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiDocumentAnalyzeResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long analysisId;

    private String taskType;

    private String answer;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long suggestedKnowledgeBaseId;

    private String suggestedKnowledgeBaseName;

    private String suggestedDocumentName;

    private String summary;

    private List<String> tags;

    private String recommendedAction;

    private String reason;

    private Boolean canUpload;

    private String uploadDeniedReason;
}
