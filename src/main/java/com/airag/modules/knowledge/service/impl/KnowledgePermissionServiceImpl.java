package com.airag.modules.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import com.airag.common.exception.BusinessException;
import com.airag.modules.auth.entity.SysKbPermission;
import com.airag.modules.auth.entity.SysRole;
import com.airag.modules.auth.entity.SysUser;
import com.airag.modules.auth.entity.SysUserRole;
import com.airag.modules.auth.mapper.SysKbPermissionMapper;
import com.airag.modules.auth.mapper.SysRoleMapper;
import com.airag.modules.auth.mapper.SysUserMapper;
import com.airag.modules.auth.mapper.SysUserRoleMapper;
import com.airag.modules.knowledge.dto.GrantKnowledgePermissionRequest;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgePermissionService;
import com.airag.modules.knowledge.vo.KnowledgePermissionGrantVO;
import com.airag.modules.knowledge.vo.KnowledgePermissionUserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgePermissionServiceImpl implements KnowledgePermissionService {

    private static final String USER_ROLE = "USER";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysKbPermissionMapper sysKbPermissionMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public List<KnowledgePermissionUserVO> listAssignableUsers() {
        Map<Long, List<String>> roleMap = buildUserRoleMap();
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "ACTIVE")
                        .orderByAsc(SysUser::getUsername))
                .stream()
                .map(user -> KnowledgePermissionUserVO.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .nickname(resolveDisplayName(user))
                        .roles(roleMap.getOrDefault(user.getId(), List.of(USER_ROLE)))
                        .build())
                .filter(user -> isOrdinaryUser(user.getRoles()))
                .toList();
    }

    @Override
    public List<KnowledgePermissionGrantVO> listGrants(Long knowledgeBaseId) {
        knowledgeBaseService.assertExists(knowledgeBaseId);
        Map<Long, SysUser> userMap = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "ACTIVE"))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, List<String>> roleMap = buildUserRoleMap();
        return sysKbPermissionMapper.selectList(new LambdaQueryWrapper<SysKbPermission>()
                        .eq(SysKbPermission::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(SysKbPermission::getCreateTime))
                .stream()
                .map(permission -> {
                    SysUser user = userMap.get(permission.getUserId());
                    if (user == null) {
                        return null;
                    }
                    List<String> roles = roleMap.getOrDefault(user.getId(), List.of(USER_ROLE));
                    if (!isOrdinaryUser(roles)) {
                        return null;
                    }
                    return KnowledgePermissionGrantVO.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .nickname(resolveDisplayName(user))
                            .roles(roles)
                            .permissionType(permission.getPermissionType())
                            .grantedAt(permission.getCreateTime())
                            .build();
                })
                .filter(item -> item != null)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(Long knowledgeBaseId, GrantKnowledgePermissionRequest request) {
        knowledgeBaseService.assertExists(knowledgeBaseId);
        SysUser user = sysUserMapper.selectById(request.getUserId());
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("授权用户不存在");
        }

        List<String> roles = buildUserRoleMap().getOrDefault(user.getId(), List.of(USER_ROLE));
        if (!isOrdinaryUser(roles)) {
            throw new BusinessException("只能给普通用户授权知识库");
        }

        String permissionType = request.getPermissionType().trim().toUpperCase();
        if (!List.of("READ", "WRITE").contains(permissionType)) {
            throw new BusinessException("权限类型仅支持 READ 或 WRITE");
        }

        SysKbPermission existing = sysKbPermissionMapper.selectOne(new LambdaQueryWrapper<SysKbPermission>()
                .eq(SysKbPermission::getKnowledgeBaseId, knowledgeBaseId)
                .eq(SysKbPermission::getUserId, request.getUserId())
                .last("limit 1"));

        if (existing != null) {
            existing.setPermissionType(permissionType);
            sysKbPermissionMapper.updateById(existing);
            return;
        }

        SysKbPermission permission = new SysKbPermission();
        permission.setId(IdUtil.getSnowflakeNextId());
        permission.setKnowledgeBaseId(knowledgeBaseId);
        permission.setUserId(request.getUserId());
        permission.setPermissionType(permissionType);
        permission.setCreateTime(LocalDateTime.now());
        sysKbPermissionMapper.insert(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long knowledgeBaseId, Long userId) {
        knowledgeBaseService.assertExists(knowledgeBaseId);
        sysKbPermissionMapper.delete(new LambdaQueryWrapper<SysKbPermission>()
                .eq(SysKbPermission::getKnowledgeBaseId, knowledgeBaseId)
                .eq(SysKbPermission::getUserId, userId));
    }

    private Map<Long, List<String>> buildUserRoleMap() {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>());
        if (userRoles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().toList();
        Map<Long, String> roleCodeMap = sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getDeleted() == null || role.getDeleted() == 0)
                .filter(role -> "ACTIVE".equalsIgnoreCase(role.getStatus()))
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode));
        return userRoles.stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(
                                userRole -> roleCodeMap.get(userRole.getRoleId()),
                                Collectors.filtering(role -> role != null, Collectors.toList())
                        )
                ));
    }

    private boolean isOrdinaryUser(List<String> roles) {
        return roles.size() == 1 && USER_ROLE.equals(roles.get(0));
    }

    private String resolveDisplayName(SysUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }
}
