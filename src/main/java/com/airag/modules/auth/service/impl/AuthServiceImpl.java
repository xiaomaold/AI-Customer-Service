package com.airag.modules.auth.service.impl;

import com.airag.modules.auth.dto.LoginRequest;
import com.airag.modules.auth.entity.SysRole;
import com.airag.modules.auth.entity.SysUser;
import com.airag.modules.auth.entity.SysUserRole;
import com.airag.modules.auth.mapper.SysRoleMapper;
import com.airag.modules.auth.mapper.SysUserMapper;
import com.airag.modules.auth.mapper.SysUserRoleMapper;
import com.airag.modules.auth.security.JwtTokenService;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.auth.service.AuthService;
import com.airag.modules.auth.vo.LoginVO;
import com.airag.modules.auth.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .last("limit 1"));
        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(resolveDisplayName(user))
                .roles(listRoleCodes(user.getId()))
                .build();

        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));

        return LoginVO.builder()
                .accessToken(jwtTokenService.generateToken(loginUser))
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpirationSeconds())
                .userInfo(currentUser(loginUser))
                .build();
    }

    @Override
    public UserInfoVO currentUser(LoginUser loginUser) {
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        return UserInfoVO.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .userType(user == null ? null : user.getUserType())
                .roles(loginUser.getRoles())
                .build();
    }

    private List<String> listRoleCodes(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of("USER");
        }

        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().toList();
        Map<Long, SysRole> roleMap = sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getDeleted() == null || role.getDeleted() == 0)
                .filter(role -> "ACTIVE".equalsIgnoreCase(role.getStatus()))
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));

        return userRoles.stream()
                .map(item -> roleMap.get(item.getRoleId()))
                .filter(role -> role != null)
                .map(SysRole::getRoleCode)
                .distinct()
                .toList();
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
