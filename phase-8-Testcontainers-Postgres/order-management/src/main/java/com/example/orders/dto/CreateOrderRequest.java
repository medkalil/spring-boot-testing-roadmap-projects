package com.example.orders.dto;

import java.util.List;

public record CreateOrderRequest(
        Long customerId,
        List<OrderItemRequest> items
) {
}