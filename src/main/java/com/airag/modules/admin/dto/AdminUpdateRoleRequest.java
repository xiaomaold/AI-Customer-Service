package com.airag.modules.admin.dto;

import lombok.Data;

@Data
public class AdminUpdateRoleRequest {

    private String roleName;
    private String status;
    private String remark;
}
