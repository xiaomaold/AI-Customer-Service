package com.airag.modules.admin.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.common.exception.BusinessException;
import com.airag.modules.admin.dto.AdminCreateRoleRequest;
import com.airag.modules.admin.dto.AdminCreateUserRequest;
import com.airag.modules.admin.dto.AdminResetPasswordRequest;
import com.airag.modules.admin.dto.AdminSaveConfigRequest;
import com.airag.modules.admin.dto.AdminUpdateRoleRequest;
import com.airag.modules.admin.dto.AdminUpdateUserRequest;
import com.airag.modules.admin.entity.SysConfig;
import com.airag.modules.admin.mapper.SysConfigMapper;
import com.airag.modules.admin.service.AdminService;
import com.airag.modules.admin.vo.AdminConfigVO;
import com.airag.modules.admin.vo.AdminRoleVO;
import com.airag.modules.admin.vo.AdminUserVO;
import com.airag.modules.auth.entity.SysRole;
import com.airag.modules.auth.entity.SysUser;
import com.airag.modules.auth.entity.SysUserRole;
import com.airag.modules.auth.mapper.SysRoleMapper;
import com.airag.modules.auth.mapper.SysUserMapper;
import com.airag.modules.auth.mapper.SysUserRoleMapper;
import com.airag.modules.auth.security.SecurityUtils;
import com.airag.modules.integration.businesscenter.service.BusinessCenterUserSyncService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysConfigMapper sysConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final BusinessCenterUserSyncService businessCenterUserSyncService;

    @Override
    public List<AdminUserVO> listUsers(String keyword, String status, String roleCode) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreateTime));
        Map<Long, List<SysRole>> userRoleMap = buildUserRoleEntityMap();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(status);
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        return users.stream()
                .map(user -> toUserVO(user, userRoleMap.getOrDefault(user.getId(), List.of())))
                .filter(user -> matchesUser(user, normalizedKeyword, normalizedStatus, normalizedRoleCode))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO createUser(AdminCreateUserRequest request) {
        String username = request.getUsername().trim();
        ensureUsernameUnique(username, null);

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setId(IdUtil.getSnowflakeNextId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        user.setNickname(trimToNull(request.getNickname()));
        user.setRealName(trimToNull(request.getRealName()));
        user.setMobile(trimToNull(request.getMobile()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setUserType(normalizeUserType(request.getUserType()));
        user.setStatus(normalizeStatus(request.getStatus()));
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleted(0);
        sysUserMapper.insert(user);
        businessCenterUserSyncService.syncUser(user);

        replaceUserRoles(user.getId(), request.getRoleIds());
        return toUserVO(user, loadRolesByUserId(user.getId()));
    }

    @Override
    public AdminUserVO updateUser(Long userId, AdminUpdateUserRequest request) {
        return updateUser(userId, request, SecurityUtils.getCurrentUserId());
    }


    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updateUser(Long userId, AdminUpdateUserRequest request, Long currentUserId) {
        SysUser user = getExistingUser(userId);
        user.setNickname(trimToNull(request.getNickname()));
        user.setRealName(trimToNull(request.getRealName()));
        user.setMobile(trimToNull(request.getMobile()));
        user.setEmail(trimToNull(request.getEmail()));
        if (request.getUserType() != null) {
            user.setUserType(normalizeUserType(request.getUserType()));
        }
        if (request.getStatus() != null) {
            user.setStatus(normalizeStatus(request.getStatus()));
        }
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(user);
        businessCenterUserSyncService.syncUser(user);

        if (request.getRoleIds() != null) {
            if (userId.equals(currentUserId)) {
                throw new BusinessException("不允许修改当前登录管理员自己的角色");
            }
            replaceUserRoles(userId, request.getRoleIds());
        }
        return toUserVO(user, loadRolesByUserId(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, AdminResetPasswordRequest request) {
        SysUser user = getExistingUser(userId);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new BusinessException("不能删除当前登录账号");
        }
        SysUser user = getExistingUser(userId);
        boolean isBuiltInAdmin = loadRolesByUserId(userId).stream().anyMatch(role -> "ADMIN".equals(role.getRoleCode()));
        if (isBuiltInAdmin) {
            throw new BusinessException("管理员账号不允许直接删除");
        }
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        sysUserMapper.deleteById(user.getId());
    }

    @Override
    public List<AdminRoleVO> listRoles(String keyword, String status) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(status);
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .orderByAsc(SysRole::getRoleCode))
                .stream()
                .map(this::toRoleVO)
                .filter(role -> matchesRole(role, normalizedKeyword, normalizedStatus))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRoleVO createRole(AdminCreateRoleRequest request) {
        String roleCode = request.getRoleCode().trim().toUpperCase();
        ensureRoleCodeUnique(roleCode, null);

        LocalDateTime now = LocalDateTime.now();
        SysRole role = new SysRole();
        role.setId(IdUtil.getSnowflakeNextId());
        role.setRoleCode(roleCode);
        role.setRoleName(request.getRoleName().trim());
        role.setStatus(normalizeStatus(request.getStatus()));
        role.setRemark(trimToNull(request.getRemark()));
        role.setCreateTime(now);
        role.setUpdateTime(now);
        role.setDeleted(0);
        sysRoleMapper.insert(role);
        return toRoleVO(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRoleVO updateRole(Long roleId, AdminUpdateRoleRequest request) {
        SysRole role = getExistingRole(roleId);
        if (request.getRoleName() != null && !request.getRoleName().isBlank()) {
            role.setRoleName(request.getRoleName().trim());
        }
        if (request.getStatus() != null) {
            role.setStatus(normalizeStatus(request.getStatus()));
        }
        role.setRemark(trimToNull(request.getRemark()));
        role.setUpdateTime(LocalDateTime.now());
        sysRoleMapper.updateById(role);
        return toRoleVO(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        SysRole role = getExistingRole(roleId);
        if (List.of("ADMIN", "KB_ADMIN", "USER").contains(role.getRoleCode())) {
            throw new BusinessException("系统内置角色不允许删除");
        }
        long count = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        if (count > 0) {
            throw new BusinessException("该角色已分配给用户，无法删除");
        }
        sysRoleMapper.deleteById(role.getId());
    }

    @Override
    public List<AdminConfigVO> listConfigs(String keyword, String status) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(status);
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                        .orderByAsc(SysConfig::getConfigKey))
                .stream()
                .map(this::toConfigVO)
                .filter(config -> matchesConfig(config, normalizedKeyword, normalizedStatus))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminConfigVO saveConfig(AdminSaveConfigRequest request) {
        String configKey = request.getConfigKey().trim();
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (config == null) {
            config = new SysConfig();
            config.setId(IdUtil.getSnowflakeNextId());
            config.setConfigKey(configKey);
            config.setCreateTime(now);
            config.setDeleted(0);
        }
        config.setConfigName(request.getConfigName().trim());
        config.setConfigValue(trimToNull(request.getConfigValue()));
        config.setValueType(StrUtil.blankToDefault(trimToNull(request.getValueType()), "STRING"));
        config.setRemark(trimToNull(request.getRemark()));
        config.setStatus(normalizeStatus(request.getStatus()));
        config.setUpdateTime(now);

        if (sysConfigMapper.selectById(config.getId()) == null) {
            sysConfigMapper.insert(config);
        } else {
            sysConfigMapper.updateById(config);
        }
        return toConfigVO(config);
    }

    private Map<Long, List<SysRole>> buildUserRoleEntityMap() {
        List<SysUserRole> relations = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>());
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).distinct().toList();
        Map<Long, SysRole> roleMap = sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getDeleted() == null || role.getDeleted() == 0)
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        Map<Long, List<SysRole>> userRoleMap = new LinkedHashMap<>();
        for (SysUserRole relation : relations) {
            SysRole role = roleMap.get(relation.getRoleId());
            if (role == null) {
                continue;
            }
            userRoleMap.computeIfAbsent(relation.getUserId(), key -> new java.util.ArrayList<>()).add(role);
        }
        return userRoleMap;
    }

    private List<SysRole> loadRolesByUserId(Long userId) {
        return buildUserRoleEntityMap().getOrDefault(userId, List.of());
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<Long> targetRoleIds = roleIds == null || roleIds.isEmpty() ? List.of(findUserRoleId()) : roleIds.stream().distinct().toList();
        List<SysRole> roles = sysRoleMapper.selectBatchIds(targetRoleIds);
        if (roles.size() != targetRoleIds.size()) {
            throw new BusinessException("存在无效角色");
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : targetRoleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setId(IdUtil.getSnowflakeNextId());
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setCreateTime(now);
            sysUserRoleMapper.insert(userRole);
        }
    }

    private Long findUserRoleId() {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "USER")
                .last("limit 1"));
        if (role == null) {
            throw new BusinessException("默认角色 USER 不存在");
        }
        return role.getId();
    }

    private void ensureUsernameUnique(String username, Long excludeUserId) {
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        if (existing != null && !existing.getId().equals(excludeUserId)) {
            throw new BusinessException("用户名已存在");
        }
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludeRoleId) {
        SysRole existing = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .last("limit 1"));
        if (existing != null && !existing.getId().equals(excludeRoleId)) {
            throw new BusinessException("角色编码已存在");
        }
    }

    private SysUser getExistingUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private SysRole getExistingRole(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || (role.getDeleted() != null && role.getDeleted() == 1)) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private String normalizeStatus(String status) {
        String value = StrUtil.blankToDefault(trimToNull(status), "ACTIVE").toUpperCase();
        if (!List.of("ACTIVE", "DISABLED").contains(value)) {
            throw new BusinessException("状态仅支持 ACTIVE 或 DISABLED");
        }
        return value;
    }

    private String normalizeUserType(String userType) {
        String value = StrUtil.blankToDefault(trimToNull(userType), "CUSTOMER").toUpperCase();
        if (!List.of("EMPLOYEE", "CUSTOMER").contains(value)) {
            throw new BusinessException("用户类型仅支持 EMPLOYEE 或 CUSTOMER");
        }
        return value;
    }

    private String normalizeRoleCode(String roleCode) {
        return roleCode == null || roleCode.isBlank() ? null : roleCode.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private boolean matchesUser(AdminUserVO user, String keyword, String status, String roleCode) {
        boolean matchesKeyword = keyword == null
                || containsIgnoreCase(user.getUsername(), keyword)
                || containsIgnoreCase(user.getNickname(), keyword)
                || containsIgnoreCase(user.getRealName(), keyword)
                || containsIgnoreCase(user.getEmail(), keyword);
        boolean matchesStatus = status == null || status.equalsIgnoreCase(user.getStatus());
        boolean matchesRole = roleCode == null || user.getRoles().stream().anyMatch(role -> roleCode.equalsIgnoreCase(role));
        return matchesKeyword && matchesStatus && matchesRole;
    }

    private boolean matchesRole(AdminRoleVO role, String keyword, String status) {
        boolean matchesKeyword = keyword == null
                || containsIgnoreCase(role.getRoleCode(), keyword)
                || containsIgnoreCase(role.getRoleName(), keyword)
                || containsIgnoreCase(role.getRemark(), keyword);
        boolean matchesStatus = status == null || status.equalsIgnoreCase(role.getStatus());
        return matchesKeyword && matchesStatus;
    }

    private boolean matchesConfig(AdminConfigVO config, String keyword, String status) {
        boolean matchesKeyword = keyword == null
                || containsIgnoreCase(config.getConfigKey(), keyword)
                || containsIgnoreCase(config.getConfigName(), keyword)
                || containsIgnoreCase(config.getRemark(), keyword);
        boolean matchesStatus = status == null || status.equalsIgnoreCase(config.getStatus());
        return matchesKeyword && matchesStatus;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private AdminUserVO toUserVO(SysUser user, List<SysRole> roles) {
        List<SysRole> safeRoles = roles == null ? Collections.emptyList() : roles;
        return AdminUserVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .realName(user.getRealName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .userType(user.getUserType())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .roles(safeRoles.stream().map(SysRole::getRoleCode).toList())
                .roleIds(safeRoles.stream().map(SysRole::getId).toList())
                .build();
    }

    private AdminRoleVO toRoleVO(SysRole role) {
        return AdminRoleVO.builder()
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .status(role.getStatus())
                .remark(role.getRemark())
                .createTime(role.getCreateTime())
                .build();
    }

    private AdminConfigVO toConfigVO(SysConfig config) {
        return AdminConfigVO.builder()
                .configId(config.getId())
                .configKey(config.getConfigKey())
                .configName(config.getConfigName())
                .configValue(config.getConfigValue())
                .valueType(config.getValueType())
                .remark(config.getRemark())
                .status(config.getStatus())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
