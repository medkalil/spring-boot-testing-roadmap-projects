package com.example.multitenant.dto;

// POST /api/auth/login -> 200
// { "accessToken": "eyJhbGciOi...", "refreshToken": "eyJhbGciOi..." }
//
// The accessToken is what tests send back as:
//     Authorization: Bearer <accessToken>
// The refreshToken follows the same JWT design with a longer lifetime; the
// refresh flow itself (POST /api/auth/refresh) is out of scope for Phase 10.
public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}