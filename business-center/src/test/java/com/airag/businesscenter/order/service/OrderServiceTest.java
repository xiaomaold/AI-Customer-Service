package com.airag.businesscenter.order.service;

import com.airag.businesscenter.common.BusinessException;
import com.airag.businesscenter.common.IdGenerator;
import com.airag.businesscenter.order.domain.Order;
import com.airag.businesscenter.order.domain.OrderStatus;
import com.airag.businesscenter.order.dto.CancelOrderRequest;
import com.airag.businesscenter.order.dto.CreateOrderRequest;
import com.airag.businesscenter.order.repository.OrderRepository;
import com.airag.businesscenter.product.domain.Product;
import com.airag.businesscenter.product.repository.ProductRepository;
import com.airag.businesscenter.product.service.ProductService;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.user.repository.UserRepository;
import com.airag.businesscenter.user.service.UserDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderService orderService;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        IdGenerator idGenerator = new IdGenerator();
        UserRepository userRepository = mock(UserRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        orderRepository = mock(OrderRepository.class);

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

        orderService = new OrderService(idGenerator, userDirectoryService, productService, orderRepository);
    }

    @Test
    void shouldCreateCustomerOrderAsUnpaid() {
        Order order = orderService.create(new CreateOrderRequest(2001L, "P-1001", 2, null));

        assertEquals(OrderStatus.UNPAID, order.status());
        assertEquals(new BigDecimal("11998.00"), order.totalAmount());
    }

    @Test
    void shouldRejectEmployeeCreatingOrder() {
        assertThrows(BusinessException.class,
                () -> orderService.create(new CreateOrderRequest(1001L, "P-1001", 1, null)));
    }

    @Test
    void shouldRejectCreatingOrderWithoutQuantity() {
        assertThrows(BusinessException.class,
                () -> orderService.create(new CreateOrderRequest(2001L, "P-1001", null, null)));
    }

    @Test
    void shouldCancelOnlyUnpaidOrder() {
        Order created = orderService.create(new CreateOrderRequest(2001L, "P-1001", 1, null));
        when(orderRepository.findByOrderNo(created.orderNo())).thenReturn(Optional.of(created));

        Order cancelled = orderService.cancel(created.orderNo(), new CancelOrderRequest(2001L, "changed mind"));
        assertEquals(OrderStatus.CANCELLED, cancelled.status());

        when(orderRepository.findByOrderNo(created.orderNo())).thenReturn(Optional.of(cancelled));
        assertThrows(BusinessException.class,
                () -> orderService.cancel(created.orderNo(), new CancelOrderRequest(2001L, "retry")));
    }

    @Test
    void shouldRejectCancellingPaidOrder() {
        Order created = orderService.create(new CreateOrderRequest(2001L, "P-1001", 1, null));
        Order paidOrder = created.withStatus(OrderStatus.PAID, null);
        when(orderRepository.findByOrderNo(created.orderNo())).thenReturn(Optional.of(paidOrder));

        assertThrows(BusinessException.class,
                () -> orderService.cancel(created.orderNo(), new CancelOrderRequest(2001L, "cannot cancel paid")));
    }
}
