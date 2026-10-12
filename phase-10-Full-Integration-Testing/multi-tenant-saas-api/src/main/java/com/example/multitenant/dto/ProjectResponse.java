package com.example.multitenant.dto;

// organizationId is part of every project response on purpose: tests assert
// both the HTTP status AND that the row landed in the right tenant.
public record ProjectResponse(
        Long id,
        String name,
        Long organizationId
) {
}