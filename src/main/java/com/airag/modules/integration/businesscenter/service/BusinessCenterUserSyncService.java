package com.airag.modules.integration.businesscenter.service;

import com.airag.modules.auth.entity.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BusinessCenterUserSyncService {

    private final JdbcTemplate jdbcTemplate;

    public BusinessCenterUserSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncUser(SysUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO business_center.bc_user (id, username, display_name, user_type, created_time, updated_time)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    username = VALUES(username),
                    display_name = VALUES(display_name),
                    user_type = VALUES(user_type),
                    updated_time = VALUES(updated_time)
                """,
                user.getId(),
                user.getUsername(),
                resolveDisplayName(user),
                normalizeUserType(user.getUserType()),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    private String resolveDisplayName(SysUser user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName().trim();
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        return user.getUsername();
    }

    private String normalizeUserType(String userType) {
        if ("EMPLOYEE".equalsIgnoreCase(userType)) {
            return "EMPLOYEE";
        }
        return "CUSTOMER";
    }
}
