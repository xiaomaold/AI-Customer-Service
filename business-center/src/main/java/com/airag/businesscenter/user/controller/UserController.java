package com.airag.businesscenter.user.controller;

import com.airag.businesscenter.common.ApiResponse;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.service.UserDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/users")
public class UserController {

    private final UserDirectoryService userDirectoryService;

    public UserController(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    @GetMapping
    public ApiResponse<List<BusinessUser>> listUsers() {
        return ApiResponse.success(userDirectoryService.listUsers());
    }
}
