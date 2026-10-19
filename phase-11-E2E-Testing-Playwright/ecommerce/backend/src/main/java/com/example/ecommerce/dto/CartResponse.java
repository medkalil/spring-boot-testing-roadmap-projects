package com.example.ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

/** The whole cart plus its server-computed total. */
public record CartResponse(List<CartItemResponse> items, BigDecimal total) {}