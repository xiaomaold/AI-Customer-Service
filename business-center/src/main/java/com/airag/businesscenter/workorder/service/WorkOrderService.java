package com.airag.businesscenter.workorder.service;

import com.airag.businesscenter.common.BusinessException;
import com.airag.businesscenter.common.IdGenerator;
import com.airag.businesscenter.order.service.OrderService;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.user.service.UserDirectoryService;
import com.airag.businesscenter.workorder.domain.LeaveType;
import com.airag.businesscenter.workorder.domain.WorkOrder;
import com.airag.businesscenter.workorder.domain.WorkOrderSourceChannel;
import com.airag.businesscenter.workorder.domain.WorkOrderStatus;
import com.airag.businesscenter.workorder.domain.WorkOrderType;
import com.airag.businesscenter.workorder.dto.CreateWorkOrderRequest;
import com.airag.businesscenter.workorder.dto.UpdateWorkOrderStatusRequest;
import com.airag.businesscenter.workorder.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WorkOrderService {

    private final IdGenerator idGenerator;
    private final UserDirectoryService userDirectoryService;
    private final OrderService orderService;
    private final WorkOrderRepository workOrderRepository;

    public WorkOrderService(IdGenerator idGenerator,
                            UserDirectoryService userDirectoryService,
                            OrderService orderService,
                            WorkOrderRepository workOrderRepository) {
        this.idGenerator = idGenerator;
        this.userDirectoryService = userDirectoryService;
        this.orderService = orderService;
        this.workOrderRepository = workOrderRepository;
    }

    public WorkOrder create(CreateWorkOrderRequest request) {
        BusinessUser user = userDirectoryService.requireUser(request.userId());
        Map<String, Object> extData = request.extData() == null ? Map.of() : request.extData();
        validateCreateRule(user, request.workOrderType(), extData);

        String relatedOrderNo = null;
        String title = buildTitle(user, request.workOrderType());
        if (request.workOrderType() == WorkOrderType.REFUND) {
            relatedOrderNo = stringValue(extData.get("orderNo"));
        }
        LocalDateTime now = LocalDateTime.now();
        WorkOrder workOrder = new WorkOrder(
                idGenerator.nextNumericId(),
                idGenerator.nextBusinessNo("WO"),
                user.id(),
                user.userType(),
                request.workOrderType(),
                WorkOrderStatus.PENDING,
                title,
                request.content(),
                relatedOrderNo,
                extData,
                null,
                null,
                null,
                null,
                request.sourceChannel() == null ? WorkOrderSourceChannel.AI_CHAT : request.sourceChannel(),
                now,
                now
        );
        workOrderRepository.insert(workOrder);
        return workOrder;
    }

    public WorkOrder requireByWorkOrderNo(String workOrderNo) {
        return workOrderRepository.findByWorkOrderNo(workOrderNo)
                .orElseThrow(() -> new BusinessException("WORK_ORDER_NOT_FOUND", "工单不存在：" + workOrderNo));
    }

    public List<WorkOrder> listWorkOrders() {
        return workOrderRepository.findAll();
    }

    public WorkOrder updateStatus(String workOrderNo, UpdateWorkOrderStatusRequest request) {
        WorkOrder current = requireByWorkOrderNo(workOrderNo);
        validateStatusChange(request);
        WorkOrder updated = current.withStatus(
                request.status(),
                request.processedBy(),
                request.processRemark(),
                request.rejectReason()
        );
        workOrderRepository.updateStatus(updated);
        return updated;
    }

    public void save(WorkOrder workOrder) {
        if (workOrderRepository.findByWorkOrderNo(workOrder.workOrderNo()).isPresent()) {
            workOrderRepository.updateStatus(workOrder);
            return;
        }
        workOrderRepository.insert(workOrder);
    }

    private void validateCreateRule(BusinessUser user, WorkOrderType type, Map<String, Object> extData) {
        if (type == WorkOrderType.LEAVE) {
            if (user.userType() != UserType.EMPLOYEE) {
                throw new BusinessException("LEAVE_ONLY_EMPLOYEE", "只有员工可以提交请假工单");
            }
            Integer leaveDays = integerValue(extData.get("leaveDays"));
            String leaveType = stringValue(extData.get("leaveType"));
            if (leaveDays == null || leaveDays <= 0) {
                throw new BusinessException("LEAVE_DAYS_REQUIRED", "请填写大于 0 的请假天数");
            }
            if (leaveType == null) {
                throw new BusinessException("LEAVE_TYPE_REQUIRED", "请填写请假类型");
            }
            try {
                LeaveType.valueOf(leaveType.toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("LEAVE_TYPE_INVALID", "请假类型不合法");
            }
            return;
        }

        if (user.userType() != UserType.CUSTOMER) {
            throw new BusinessException("CUSTOMER_ONLY_WORK_ORDER", "只有客户可以提交该类型工单");
        }

        if (type == WorkOrderType.REFUND) {
            String orderNo = stringValue(extData.get("orderNo"));
            if (orderNo == null) {
                throw new BusinessException("ORDER_NO_REQUIRED", "退款工单必须提供订单号");
            }
            if (!orderService.isRefundableForUser(orderNo, user.id())) {
                throw new BusinessException("ORDER_NOT_REFUNDABLE", "只有当前用户自己的已付款订单才可以申请退款");
            }
        }
    }

    private void validateStatusChange(UpdateWorkOrderStatusRequest request) {
        if (request.status() == WorkOrderStatus.REJECTED && isBlank(request.rejectReason())) {
            throw new BusinessException("REJECT_REASON_REQUIRED", "工单驳回时必须填写驳回原因");
        }
    }

    private String buildTitle(BusinessUser user, WorkOrderType type) {
        return switch (type) {
            case LEAVE -> user.displayName() + "请假申请";
            case REFUND -> user.displayName() + "退款申请";
            case HUMAN_SERVICE -> user.displayName() + "转人工申请";
        };
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
