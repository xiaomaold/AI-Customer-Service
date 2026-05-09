package com.airag.businesscenter.user.repository;

import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BusinessUser> findById(Long id) {
        List<BusinessUser> users = jdbcTemplate.query(
                "SELECT id, username, display_name, user_type, created_time, updated_time FROM bc_user WHERE id = ?",
                rowMapper(),
                id
        );
        return users.stream().findFirst();
    }

    public List<BusinessUser> findAll() {
        return jdbcTemplate.query(
                "SELECT id, username, display_name, user_type, created_time, updated_time FROM bc_user ORDER BY id",
                rowMapper()
        );
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM bc_user WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM bc_user", Long.class);
        return count == null ? 0L : count;
    }

    public void insert(BusinessUser user) {
        jdbcTemplate.update(
                "INSERT INTO bc_user (id, username, display_name, user_type, created_time, updated_time) VALUES (?, ?, ?, ?, ?, ?)",
                user.id(),
                user.username(),
                user.displayName(),
                user.userType().name(),
                user.createdTime(),
                user.updatedTime()
        );
    }

    private RowMapper<BusinessUser> rowMapper() {
        return (rs, rowNum) -> new BusinessUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                UserType.valueOf(rs.getString("user_type")),
                toLocalDateTime(rs, "created_time"),
                toLocalDateTime(rs, "updated_time")
        );
    }

    private LocalDateTime toLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toLocalDateTime();
    }
}
