package com.example.multitenant.dto;

// POST /api/auth/login - note: login identifies by EMAIL, not username.
// { "email": "alice@example.com", "password": "password123" }
public record LoginRequest(
        String email,
        String password
) {
}