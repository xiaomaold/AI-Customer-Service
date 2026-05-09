package com.airag.modules.auth.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.auth.dto.LoginRequest;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.auth.service.AuthService;
import com.airag.modules.auth.vo.LoginVO;
import com.airag.modules.auth.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me() {
        return ApiResponse.success(authService.currentUser(SecurityUtils.getCurrentUser()));
    }
}
