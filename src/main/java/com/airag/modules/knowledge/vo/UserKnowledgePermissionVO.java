package com.airag.modules.knowledge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserKnowledgePermissionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String description;
    private String permissionType;
    private String status;
}
