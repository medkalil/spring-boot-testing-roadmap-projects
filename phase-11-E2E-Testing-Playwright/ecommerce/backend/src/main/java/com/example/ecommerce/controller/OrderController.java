package com.example.ecommerce.controller;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Order endpoints. All require a token; every lookup is scoped to the caller's
 * email, so order history is strictly per-customer and a stranger's order id
 * answers 404 (see OrderRepository.findByIdAndUserEmail).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 201 with the created order; 400 for an empty cart or blank address; 409 for stock/availability. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(Authentication authentication, @RequestBody OrderRequest request) {
        return orderService.createOrder(authentication.getName(), request);
    }

    @GetMapping
    public List<OrderResponse> listOrders(Authentication authentication) {
        return orderService.listOrders(authentication.getName());
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(Authentication authentication, @PathVariable Long orderId) {
        return orderService.getOrder(authentication.getName(), orderId);
    }
}