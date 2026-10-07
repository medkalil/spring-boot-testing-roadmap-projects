package com.example.orders.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        Long id,
        BigDecimal total,
        CustomerResponse customer,
        List<OrderItemResponse> items
) {
}