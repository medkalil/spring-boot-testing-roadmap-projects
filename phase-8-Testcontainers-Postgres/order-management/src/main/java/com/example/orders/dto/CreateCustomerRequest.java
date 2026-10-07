package com.example.orders.dto;

public record CreateCustomerRequest(
        String name,
        String email
) {
}