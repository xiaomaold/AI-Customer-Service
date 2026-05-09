package com.airag.modules.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeDocumentUploadResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    private String documentName;

    private Integer chunkCount;

    private String parseStatus;
}
