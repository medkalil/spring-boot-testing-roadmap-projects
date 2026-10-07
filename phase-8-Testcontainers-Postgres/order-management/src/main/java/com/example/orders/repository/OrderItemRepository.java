package com.example.orders.repository;

import com.example.orders.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {

    // Useful for asserting what the database really stored (Phase 8.10).
    List<OrderItem> findByOrderId(Long orderId);
}