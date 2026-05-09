package com.airag.modules.knowledge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeChunkVO {

    private String id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;
    private Integer chunkIndex;
    private String fileName;
    private String content;
}
