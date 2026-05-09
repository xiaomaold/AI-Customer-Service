package com.airag.businesscenter.order.controller;

import com.airag.businesscenter.common.ApiResponse;
import com.airag.businesscenter.order.domain.Order;
import com.airag.businesscenter.order.dto.CancelOrderRequest;
import com.airag.businesscenter.order.dto.CreateOrderRequest;
import com.airag.businesscenter.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success("order created", orderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<Order>> listOrders() {
        return ApiResponse.success(orderService.listOrders());
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> getOrder(@PathVariable String orderNo) {
        return ApiResponse.success(orderService.requireByOrderNo(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<Order> cancelOrder(@PathVariable String orderNo,
                                          @Valid @RequestBody CancelOrderRequest request) {
        return ApiResponse.success("order cancelled", orderService.cancel(orderNo, request));
    }

    @PostMapping("/{orderNo}/mark-paid")
    public ApiResponse<Order> markPaid(@PathVariable String orderNo) {
        return ApiResponse.success("order marked paid", orderService.markPaid(orderNo));
    }
}
