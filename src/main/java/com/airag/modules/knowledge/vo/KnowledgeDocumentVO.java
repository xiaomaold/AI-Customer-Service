package com.airag.modules.knowledge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDocumentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;
    private String documentName;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String parseStatus;
    private Integer chunkCount;
    private String embeddingModel;
    private String remark;
    private LocalDateTime createTime;
}
