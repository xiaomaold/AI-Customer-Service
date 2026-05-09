package com.airag.modules.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminRoleVO {

    private Long roleId;
    private String roleCode;
    private String roleName;
    private String status;
    private String remark;
    private LocalDateTime createTime;
}
