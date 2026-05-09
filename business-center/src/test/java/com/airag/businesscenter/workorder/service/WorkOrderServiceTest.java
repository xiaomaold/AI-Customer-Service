package com.airag.businesscenter.workorder.service;

import com.airag.businesscenter.common.BusinessException;
import com.airag.businesscenter.common.IdGenerator;
import com.airag.businesscenter.order.domain.Order;
import com.airag.businesscenter.order.domain.OrderSourceChannel;
import com.airag.businesscenter.order.domain.OrderStatus;
import com.airag.businesscenter.order.repository.OrderRepository;
import com.airag.businesscenter.order.service.OrderService;
import com.airag.businesscenter.product.domain.Product;
import com.airag.businesscenter.product.repository.ProductRepository;
import com.airag.businesscenter.product.service.ProductService;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.user.repository.UserRepository;
import com.airag.businesscenter.user.service.UserDirectoryService;
import com.airag.businesscenter.workorder.domain.WorkOrder;
import com.airag.businesscenter.workorder.domain.WorkOrderStatus;
import com.airag.businesscenter.workorder.domain.WorkOrderType;
import com.airag.businesscenter.workorder.dto.CreateWorkOrderRequest;
import com.airag.businesscenter.workorder.dto.UpdateWorkOrderStatusRequest;
import com.airag.businesscenter.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkOrderServiceTest {

    private WorkOrderService workOrderService;
    private OrderService orderService;
    private OrderRepository orderRepository;
    private WorkOrderRepository workOrderRepository;

    @BeforeEach
    void setUp() {
        IdGenerator idGenerator = new IdGenerator();
        UserRepository userRepository = mock(UserRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        orderRepository = mock(OrderRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);

        UserDirectoryService userDirectoryService = new UserDirectoryService(userRepository);
        ProductService productService = new ProductService(idGenerator, productRepository);
        LocalDateTime now = LocalDateTime.now();

        when(userRepository.findById(1001L)).thenReturn(Optional.of(
                new BusinessUser(1001L, "employee.demo", "Employee Demo", UserType.EMPLOYEE, now, now)
        ));
        when(userRepository.findById(2001L)).thenReturn(Optional.of(
                new BusinessUser(2001L, "customer.demo", "Customer Demo", UserType.CUSTOMER, now, now)
        ));
        when(productRepository.findByProductNo("P-1001")).thenReturn(Optional.of(
                new Product(3001L, "P-1001", "Business Laptop", new BigDecimal("5999.00"), "Laptop", now, now)
        ));
        doNothing().when(orderRepository).insert(any());
        doNothing().when(workOrderRepository).insert(any());
        doNothing().when(workOrderRepository).updateStatus(any());

        orderService = new OrderService(idGenerator, userDirectoryService, productService, orderRepository);
        workOrderService = new WorkOrderService(idGenerator, userDirectoryService, orderService, workOrderRepository);
    }

    @Test
    void shouldCreateLeaveWorkOrderForEmployee() {
        WorkOrder workOrder = workOrderService.create(new CreateWorkOrderRequest(
                1001L,
                WorkOrderType.LEAVE,
                "need leave",
                Map.of("leaveDays", 3, "leaveType", "ANNUAL"),
                null
        ));

        assertEquals(WorkOrderStatus.PENDING, workOrder.status());
        assertEquals(WorkOrderType.LEAVE, workOrder.workOrderType());
    }

    @Test
    void shouldRejectLeaveWorkOrderForCustomer() {
        assertThrows(BusinessException.class, () -> workOrderService.create(new CreateWorkOrderRequest(
                2001L,
                WorkOrderType.LEAVE,
                "need leave",
                Map.of("leaveDays", 3, "leaveType", "ANNUAL"),
                null
        )));
    }

    @Test
    void shouldRequirePaidOrderForRefundWorkOrder() {
        Order unpaidOrder = orderService.create(new com.airag.businesscenter.order.dto.CreateOrderRequest(2001L, "P-1001", 1, OrderSourceChannel.AI_CHAT));
        when(orderRepository.findByOrderNo(unpaidOrder.orderNo())).thenReturn(Optional.of(unpaidOrder));

        assertThrows(BusinessException.class, () -> workOrderService.create(new CreateWorkOrderRequest(
                2001L,
                WorkOrderType.REFUND,
                "refund please",
                Map.of("orderNo", unpaidOrder.orderNo()),
                null
        )));

        Order paidOrder = unpaidOrder.withStatus(OrderStatus.PAID, null);
        when(orderRepository.findByOrderNo(unpaidOrder.orderNo())).thenReturn(Optional.of(paidOrder));

        WorkOrder workOrder = workOrderService.create(new CreateWorkOrderRequest(
                2001L,
                WorkOrderType.REFUND,
                "refund please",
                Map.of("orderNo", unpaidOrder.orderNo()),
                null
        ));
        assertEquals(WorkOrderType.REFUND, workOrder.workOrderType());
    }

    @Test
    void shouldRejectRefundWorkOrderForEmployee() {
        assertThrows(BusinessException.class, () -> workOrderService.create(new CreateWorkOrderRequest(
                1001L,
                WorkOrderType.REFUND,
                "refund please",
                Map.of("orderNo", "ORD202604220001"),
                null
        )));
    }

    @Test
    void shouldRejectHumanServiceWorkOrderForEmployee() {
        assertThrows(BusinessException.class, () -> workOrderService.create(new CreateWorkOrderRequest(
                1001L,
                WorkOrderType.HUMAN_SERVICE,
                "need support",
                Map.of(),
                null
        )));
    }

    @Test
    void shouldRequireRejectReasonWhenRejectingWorkOrder() {
        WorkOrder workOrder = workOrderService.create(new CreateWorkOrderRequest(
                2001L,
                WorkOrderType.HUMAN_SERVICE,
                "need support",
                Map.of(),
                null
        ));
        when(workOrderRepository.findByWorkOrderNo(workOrder.workOrderNo())).thenReturn(Optional.of(workOrder));

        assertThrows(BusinessException.class,
                () -> workOrderService.updateStatus(workOrder.workOrderNo(),
                        new UpdateWorkOrderStatusRequest(WorkOrderStatus.REJECTED, 9001L, "not allowed", null)));
    }
}
