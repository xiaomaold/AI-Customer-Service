package com.airag.modules.knowledge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeBaseVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String knowledgeBaseName;
    private String description;
    private String status;
    private Integer documentCount;
    private LocalDateTime createTime;
}
