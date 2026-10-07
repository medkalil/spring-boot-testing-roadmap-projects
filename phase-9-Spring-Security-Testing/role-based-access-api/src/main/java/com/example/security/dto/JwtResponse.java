package com.example.security.dto;

// POST /api/auth/login -> 200
// { "token": "eyJhbGciOi...", "type": "Bearer", "username": "john", "role": "USER" }
//
// "type" is what the caller must put in front of the token:
//     Authorization: Bearer <token>
public record JwtResponse(
        String token,
        String type,
        String username,
        String role
) {
    public JwtResponse(String token, String username, String role) {
        this(token, "Bearer", username, role);
    }
}