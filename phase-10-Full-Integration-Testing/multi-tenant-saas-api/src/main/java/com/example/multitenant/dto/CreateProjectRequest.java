package com.example.multitenant.dto;

// POST /api/organizations/{organizationId}/projects
// { "name": "Website" }
public record CreateProjectRequest(
        String name
) {
}