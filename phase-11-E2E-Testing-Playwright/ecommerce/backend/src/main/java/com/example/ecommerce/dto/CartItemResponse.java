package com.example.ecommerce.dto;

import java.math.BigDecimal;

public record CartItemResponse(Long id, Long productId, String name, BigDecimal unitPrice,
                               int quantity, BigDecimal lineTotal) {}