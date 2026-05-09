package com.airag.businesscenter.workorder.controller;

import com.airag.businesscenter.common.ApiResponse;
import com.airag.businesscenter.workorder.domain.WorkOrder;
import com.airag.businesscenter.workorder.dto.CreateWorkOrderRequest;
import com.airag.businesscenter.workorder.dto.UpdateWorkOrderStatusRequest;
import com.airag.businesscenter.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    public ApiResponse<WorkOrder> createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request) {
        return ApiResponse.success("work order created", workOrderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<WorkOrder>> listWorkOrders() {
        return ApiResponse.success(workOrderService.listWorkOrders());
    }

    @GetMapping("/{workOrderNo}")
    public ApiResponse<WorkOrder> getWorkOrder(@PathVariable String workOrderNo) {
        return ApiResponse.success(workOrderService.requireByWorkOrderNo(workOrderNo));
    }

    @PostMapping("/{workOrderNo}/status")
    public ApiResponse<WorkOrder> updateStatus(@PathVariable String workOrderNo,
                                               @Valid @RequestBody UpdateWorkOrderStatusRequest request) {
        return ApiResponse.success("work order updated", workOrderService.updateStatus(workOrderNo, request));
    }
}
