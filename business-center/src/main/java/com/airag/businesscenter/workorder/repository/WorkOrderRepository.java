package com.airag.businesscenter.workorder.repository;

import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.workorder.domain.WorkOrder;
import com.airag.businesscenter.workorder.domain.WorkOrderSourceChannel;
import com.airag.businesscenter.workorder.domain.WorkOrderStatus;
import com.airag.businesscenter.workorder.domain.WorkOrderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class WorkOrderRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkOrderRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<WorkOrder> findByWorkOrderNo(String workOrderNo) {
        List<WorkOrder> workOrders = jdbcTemplate.query(
                """
                SELECT id, work_order_no, user_id, user_type, work_order_type, status, title, content,
                       related_order_no, ext_json, reject_reason, processed_by, process_remark,
                       processed_time, source_channel, created_time, updated_time
                FROM bc_work_order
                WHERE work_order_no = ?
                """,
                rowMapper(),
                workOrderNo
        );
        return workOrders.stream().findFirst();
    }

    public List<WorkOrder> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, work_order_no, user_id, user_type, work_order_type, status, title, content,
                       related_order_no, ext_json, reject_reason, processed_by, process_remark,
                       processed_time, source_channel, created_time, updated_time
                FROM bc_work_order
                ORDER BY created_time DESC
                """,
                rowMapper()
        );
    }

    public void insert(WorkOrder workOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO bc_work_order (
                    id, work_order_no, user_id, user_type, work_order_type, status, title, content,
                    related_order_no, ext_json, reject_reason, processed_by, process_remark,
                    processed_time, source_channel, created_time, updated_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                workOrder.id(),
                workOrder.workOrderNo(),
                workOrder.userId(),
                workOrder.userType().name(),
                workOrder.workOrderType().name(),
                workOrder.status().name(),
                workOrder.title(),
                workOrder.content(),
                workOrder.relatedOrderNo(),
                toJson(workOrder.extData()),
                workOrder.rejectReason(),
                workOrder.processedBy(),
                workOrder.processRemark(),
                workOrder.processedTime(),
                workOrder.sourceChannel().name(),
                workOrder.createdTime(),
                workOrder.updatedTime()
        );
    }

    public void updateStatus(WorkOrder workOrder) {
        jdbcTemplate.update(
                """
                UPDATE bc_work_order
                SET status = ?, reject_reason = ?, processed_by = ?, process_remark = ?, processed_time = ?, updated_time = ?
                WHERE work_order_no = ?
                """,
                workOrder.status().name(),
                workOrder.rejectReason(),
                workOrder.processedBy(),
                workOrder.processRemark(),
                workOrder.processedTime(),
                workOrder.updatedTime(),
                workOrder.workOrderNo()
        );
    }

    private RowMapper<WorkOrder> rowMapper() {
        return (rs, rowNum) -> new WorkOrder(
                rs.getLong("id"),
                rs.getString("work_order_no"),
                rs.getLong("user_id"),
                UserType.valueOf(rs.getString("user_type")),
                WorkOrderType.valueOf(rs.getString("work_order_type")),
                WorkOrderStatus.valueOf(rs.getString("status")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("related_order_no"),
                fromJson(rs.getString("ext_json")),
                rs.getString("reject_reason"),
                nullableLong(rs, "processed_by"),
                rs.getString("process_remark"),
                nullableLocalDateTime(rs, "processed_time"),
                WorkOrderSourceChannel.valueOf(rs.getString("source_channel")),
                toLocalDateTime(rs, "created_time"),
                toLocalDateTime(rs, "updated_time")
        );
    }

    private String toJson(Map<String, Object> extData) {
        try {
            return objectMapper.writeValueAsString(extData == null ? Collections.emptyMap() : extData);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize work order extData", exception);
        }
    }

    private Map<String, Object> fromJson(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(extJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize work order extJson", exception);
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDateTime nullableLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        if (resultSet.getTimestamp(column) == null) {
            return null;
        }
        return resultSet.getTimestamp(column).toLocalDateTime();
    }

    private LocalDateTime toLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toLocalDateTime();
    }
}
