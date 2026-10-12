package com.example.multitenant.dto;

public record MemberResponse(
        Long userId,
        Long organizationId,
        String role
) {
}