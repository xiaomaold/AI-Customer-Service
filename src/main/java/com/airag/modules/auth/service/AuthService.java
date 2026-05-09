package com.airag.modules.auth.service;

import com.airag.modules.auth.dto.LoginRequest;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.auth.vo.LoginVO;
import com.airag.modules.auth.vo.UserInfoVO;

public interface AuthService {

    LoginVO login(LoginRequest request);

    UserInfoVO currentUser(LoginUser loginUser);
}
