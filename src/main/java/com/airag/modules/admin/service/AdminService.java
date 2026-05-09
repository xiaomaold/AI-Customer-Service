package com.airag.modules.admin.service;

import com.airag.modules.admin.dto.AdminCreateRoleRequest;
import com.airag.modules.admin.dto.AdminCreateUserRequest;
import com.airag.modules.admin.dto.AdminResetPasswordRequest;
import com.airag.modules.admin.dto.AdminSaveConfigRequest;
import com.airag.modules.admin.dto.AdminUpdateRoleRequest;
import com.airag.modules.admin.dto.AdminUpdateUserRequest;
import com.airag.modules.admin.vo.AdminConfigVO;
import com.airag.modules.admin.vo.AdminRoleVO;
import com.airag.modules.admin.vo.AdminUserVO;

import java.util.List;

public interface AdminService {

    List<AdminUserVO> listUsers(String keyword, String status, String roleCode);

    AdminUserVO createUser(AdminCreateUserRequest request);

    AdminUserVO updateUser(Long userId, AdminUpdateUserRequest request);

    void resetPassword(Long userId, AdminResetPasswordRequest request);

    void deleteUser(Long userId, Long currentUserId);

    List<AdminRoleVO> listRoles(String keyword, String status);

    AdminRoleVO createRole(AdminCreateRoleRequest request);

    AdminRoleVO updateRole(Long roleId, AdminUpdateRoleRequest request);

    void deleteRole(Long roleId);

    List<AdminConfigVO> listConfigs(String keyword, String status);

    AdminConfigVO saveConfig(AdminSaveConfigRequest request);
}
