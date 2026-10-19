package com.example.ecommerce.repository;

import com.example.ecommerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Order history for one customer, newest first. */
    List<Order> findByUserEmailOrderByCreatedAtDesc(String email);

    /**
     * Owner-scoped lookup: "does this order exist FOR THIS CALLER?"
     * A request for another customer's order id matches 0 rows, so the service
     * answers 404 and the fact that such an order exists stays hidden.
     */
    Optional<Order> findByIdAndUserEmail(Long id, String email);
}