package com.example.ecommerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String status, BigDecimal total, String shippingAddress,
                            Instant createdAt, List<OrderItemResponse> items) {}