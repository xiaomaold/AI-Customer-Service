package com.airag.businesscenter.order.service;

import com.airag.businesscenter.common.BusinessException;
import com.airag.businesscenter.common.IdGenerator;
import com.airag.businesscenter.order.domain.Order;
import com.airag.businesscenter.order.domain.OrderSourceChannel;
import com.airag.businesscenter.order.domain.OrderStatus;
import com.airag.businesscenter.order.dto.CancelOrderRequest;
import com.airag.businesscenter.order.dto.CreateOrderRequest;
import com.airag.businesscenter.order.repository.OrderRepository;
import com.airag.businesscenter.product.domain.Product;
import com.airag.businesscenter.product.service.ProductService;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.user.service.UserDirectoryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final IdGenerator idGenerator;
    private final UserDirectoryService userDirectoryService;
    private final ProductService productService;
    private final OrderRepository orderRepository;

    public OrderService(IdGenerator idGenerator,
                        UserDirectoryService userDirectoryService,
                        ProductService productService,
                        OrderRepository orderRepository) {
        this.idGenerator = idGenerator;
        this.userDirectoryService = userDirectoryService;
        this.productService = productService;
        this.orderRepository = orderRepository;
    }

    public Order create(CreateOrderRequest request) {
        BusinessUser user = userDirectoryService.requireUser(request.userId(), UserType.CUSTOMER);
        Product product = productService.requireByProductNo(request.productNo());
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessException("ORDER_QUANTITY_REQUIRED", "下单时必须提供有效的购买数量");
        }
        int quantity = request.quantity();
        BigDecimal totalAmount = product.price().multiply(BigDecimal.valueOf(quantity));
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order(
                idGenerator.nextNumericId(),
                idGenerator.nextBusinessNo("ORD"),
                user.id(),
                user.userType(),
                product.id(),
                product.productNo(),
                product.productName(),
                product.price(),
                quantity,
                totalAmount,
                OrderStatus.UNPAID,
                null,
                request.sourceChannel() == null ? OrderSourceChannel.AI_CHAT : request.sourceChannel(),
                now,
                now
        );
        orderRepository.insert(order);
        return order;
    }

    public Order requireByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在：" + orderNo));
    }

    public List<Order> listOrders() {
        return orderRepository.findAll();
    }

    public Order cancel(String orderNo, CancelOrderRequest request) {
        Order order = requireByOrderNo(orderNo);
        if (!order.userId().equals(request.userId())) {
            throw new BusinessException("ORDER_OWNER_MISMATCH", "该订单不属于当前用户");
        }
        if (order.status() != OrderStatus.UNPAID) {
            throw new BusinessException("ORDER_CANNOT_CANCEL", "只有未付款订单才可以取消");
        }
        Order updated = order.withStatus(OrderStatus.CANCELLED, request.cancelReason());
        orderRepository.updateStatus(orderNo, updated.status(), updated.cancelReason(), updated.updatedTime());
        return updated;
    }

    public Order markPaid(String orderNo) {
        Order order = requireByOrderNo(orderNo);
        if (order.status() != OrderStatus.UNPAID) {
            throw new BusinessException("ORDER_CANNOT_MARK_PAID", "只有未付款订单才可以标记为已付款");
        }
        Order updated = order.withStatus(OrderStatus.PAID, null);
        orderRepository.updateStatus(orderNo, updated.status(), updated.cancelReason(), updated.updatedTime());
        return updated;
    }

    public boolean isRefundableForUser(String orderNo, Long userId) {
        Order order = requireByOrderNo(orderNo);
        return order.userId().equals(userId) && order.status() == OrderStatus.PAID;
    }

    public void save(Order order) {
        if (orderRepository.findByOrderNo(order.orderNo()).isPresent()) {
            orderRepository.updateStatus(order.orderNo(), order.status(), order.cancelReason(), order.updatedTime());
            return;
        }
        orderRepository.insert(order);
    }
}
