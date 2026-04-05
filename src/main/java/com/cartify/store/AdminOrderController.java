package com.cartify.store;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final StoreOrderDao storeOrderDao;
    private final StoreOrderItemDao storeOrderItemDao;

    public AdminOrderController(StoreOrderDao storeOrderDao, StoreOrderItemDao storeOrderItemDao) {
        this.storeOrderDao = storeOrderDao;
        this.storeOrderItemDao = storeOrderItemDao;
    }

    @GetMapping
    public ResponseEntity<?> listOrders(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }

        List<StoreOrder> orders = storeOrderDao.findAllWithCustomerEmail();
        List<Long> ids = orders.stream().map(StoreOrder::getId).toList();
        Map<Long, List<OrderItemResponse>> itemsByOrder = storeOrderItemDao.findByOrderIds(ids).stream()
                .map(this::toItemResponse)
                .collect(Collectors.groupingBy(OrderItemResponse::orderId));

        List<OrderResponse> response = orders.stream().map(order -> new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemsByOrder.getOrDefault(order.getId(), List.of()))).toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long orderId, @RequestBody UpdateOrderStatusRequest request, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }
        if (request.status() == null || request.status().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status is required."));
        }

        StoreOrder order = storeOrderDao.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found."));
        }

        OrderStatus nextStatus;
        try {
            nextStatus = OrderStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid status."));
        }

        storeOrderDao.updateStatus(orderId, nextStatus);
        return ResponseEntity.ok(Map.of("message", "Order status updated."));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable Long orderId, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin login required."));
        }

        StoreOrder order = storeOrderDao.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found."));
        }

        storeOrderItemDao.deleteByOrderId(orderId);
        storeOrderDao.deleteById(orderId);
        return ResponseEntity.ok(Map.of("message", "Order deleted."));
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute("isAdmin"));
    }

    private OrderItemResponse toItemResponse(StoreOrderItem item) {
        return new OrderItemResponse(
                item.getOrderId(),
                item.getItemId(),
                item.getItemName(),
                item.getItemPrice(),
                item.getQuantity(),
                item.getLineTotal(),
                item.getImageUrl());
    }

    public record UpdateOrderStatusRequest(String status) {
    }

    public record OrderItemResponse(
            Long orderId,
            Long itemId,
            String itemName,
            java.math.BigDecimal itemPrice,
            Integer quantity,
            java.math.BigDecimal lineTotal,
            String imageUrl) {
    }

    public record OrderResponse(
            Long id,
            Long customerId,
            String customerEmail,
            String status,
            java.math.BigDecimal totalAmount,
            String createdAt,
            String updatedAt,
            List<OrderItemResponse> items) {
    }
}
