package com.airag.modules.knowledge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgePermissionUserVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
}
