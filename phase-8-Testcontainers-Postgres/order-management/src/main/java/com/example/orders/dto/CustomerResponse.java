package com.example.orders.dto;

public record CustomerResponse(
        Long id,
        String name,
        String email
) {
}