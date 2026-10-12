package com.example.multitenant.dto;

// POST /api/auth/register
// { "username": "alice", "email": "alice@example.com", "password": "password123" }
public record RegisterRequest(
        String username,
        String email,
        String password
) {
}