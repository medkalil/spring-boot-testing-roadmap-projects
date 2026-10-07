package com.example.security.dto;

// Deliberately does NOT contain the password: the API must never send it back.
public record UserResponse(
        Long id,
        String username,
        String role
) {
}