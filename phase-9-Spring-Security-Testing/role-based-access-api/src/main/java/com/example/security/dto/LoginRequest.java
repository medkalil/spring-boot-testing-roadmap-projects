package com.example.security.dto;

// The login body:
// { "username": "john", "password": "password" }
public record LoginRequest(
        String username,
        String password
) {
}