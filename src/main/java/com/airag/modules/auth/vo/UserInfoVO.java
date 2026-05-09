package com.airag.modules.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoVO {

    private Long userId;
    private String username;
    private String nickname;
    private String userType;
    private List<String> roles;
}
