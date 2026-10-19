package com.example.ecommerce.dto;

import java.math.BigDecimal;

public record OrderItemResponse(String productName, int quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal) {}