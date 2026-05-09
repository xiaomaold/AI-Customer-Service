package com.airag.modules.auth.security;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
@Builder
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
}
