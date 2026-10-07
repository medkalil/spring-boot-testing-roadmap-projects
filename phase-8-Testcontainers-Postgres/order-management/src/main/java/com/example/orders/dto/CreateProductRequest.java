package com.example.orders.dto;

import java.math.BigDecimal;

public record CreateProductRequest(
        String name,
        BigDecimal price
) {
}