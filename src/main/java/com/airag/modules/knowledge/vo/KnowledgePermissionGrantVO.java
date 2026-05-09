package com.airag.modules.knowledge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgePermissionGrantVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
    private String permissionType;
    private LocalDateTime grantedAt;
}
