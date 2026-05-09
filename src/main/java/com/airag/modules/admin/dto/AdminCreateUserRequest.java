package com.airag.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AdminCreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String userType;
    private String status;
    private List<Long> roleIds;
}
