package com.airag.modules.admin.controller;

import com.airag.common.result.ApiResponse;
import com.airag.modules.admin.dto.AdminCreateRoleRequest;
import com.airag.modules.admin.dto.AdminCreateUserRequest;
import com.airag.modules.admin.dto.AdminResetPasswordRequest;
import com.airag.modules.admin.dto.AdminSaveConfigRequest;
import com.airag.modules.admin.dto.AdminUpdateRoleRequest;
import com.airag.modules.admin.dto.AdminUpdateUserRequest;
import com.airag.modules.admin.service.AdminService;
import com.airag.modules.admin.service.MissedQuestionService;
import com.airag.modules.admin.vo.AdminConfigVO;
import com.airag.modules.admin.vo.AdminMissedQuestionDashboardVO;
import com.airag.modules.admin.vo.AdminMissedQuestionVO;
import com.airag.modules.admin.vo.AdminRoleVO;
import com.airag.modules.admin.vo.AdminUserVO;
import com.airag.modules.auth.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final MissedQuestionService missedQuestionService;

    @GetMapping("/users")
    public ApiResponse<List<AdminUserVO>> listUsers(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String roleCode) {
        return ApiResponse.success(adminService.listUsers(keyword, status, roleCode));
    }

    @PostMapping("/users")
    public ApiResponse<AdminUserVO> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ApiResponse.success(adminService.createUser(request));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<AdminUserVO> updateUser(@PathVariable Long userId,
                                               @RequestBody AdminUpdateUserRequest request) {
        return ApiResponse.success(adminService.updateUser(userId, request));
    }

    @PostMapping("/users/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId,
                                           @Valid @RequestBody AdminResetPasswordRequest request) {
        adminService.resetPassword(userId, request);
        return ApiResponse.success("密码重置成功", null);
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("用户删除成功", null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<AdminRoleVO>> listRoles(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.success(adminService.listRoles(keyword, status));
    }

    @PostMapping("/roles")
    public ApiResponse<AdminRoleVO> createRole(@Valid @RequestBody AdminCreateRoleRequest request) {
        return ApiResponse.success(adminService.createRole(request));
    }

    @PutMapping("/roles/{roleId}")
    public ApiResponse<AdminRoleVO> updateRole(@PathVariable Long roleId,
                                               @RequestBody AdminUpdateRoleRequest request) {
        return ApiResponse.success(adminService.updateRole(roleId, request));
    }

    @DeleteMapping("/roles/{roleId}")
    public ApiResponse<Void> deleteRole(@PathVariable Long roleId) {
        adminService.deleteRole(roleId);
        return ApiResponse.success("角色删除成功", null);
    }

    @GetMapping("/configs")
    public ApiResponse<List<AdminConfigVO>> listConfigs(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.success(adminService.listConfigs(keyword, status));
    }

    @GetMapping("/missed-questions")
    public ApiResponse<List<AdminMissedQuestionVO>> listMissedQuestions(@RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) String routeMode,
                                                                        @RequestParam(required = false) String status) {
        return ApiResponse.success(missedQuestionService.listMissedQuestions(keyword, routeMode, status));
    }

    @GetMapping("/missed-questions/dashboard")
    public ApiResponse<AdminMissedQuestionDashboardVO> getMissedQuestionDashboard() {
        return ApiResponse.success(missedQuestionService.getDashboard());
    }

    @PostMapping("/configs")
    public ApiResponse<AdminConfigVO> saveConfig(@Valid @RequestBody AdminSaveConfigRequest request) {
        return ApiResponse.success(adminService.saveConfig(request));
    }
}
