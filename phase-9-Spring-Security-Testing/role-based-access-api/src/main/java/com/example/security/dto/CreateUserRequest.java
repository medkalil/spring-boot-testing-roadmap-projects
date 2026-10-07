package com.example.security.dto;

// Exactly the body the API accepts:
// { "username": "john" }
public record CreateUserRequest(
        String username
) {
}