package com.example.orders.dto;

public record OrderItemRequest(
        Long productId,
        Integer quantity
) {
}