package com.airag.modules.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminUpdateUserRequest {

    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String userType;
    private String status;
    private List<Long> roleIds;
}
