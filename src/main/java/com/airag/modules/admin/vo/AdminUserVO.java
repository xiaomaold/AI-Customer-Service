package com.airag.modules.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminUserVO {

    private Long userId;
    private String username;
    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String userType;
    private String status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private List<String> roles;
    private List<Long> roleIds;
}
