package com.example.multitenant.dto;

// POST /api/organizations/{organizationId}/members  (ADMIN only)
// { "email": "bob@example.com", "role": "MEMBER" }
public record AddMemberRequest(
        String email,
        String role
) {
}