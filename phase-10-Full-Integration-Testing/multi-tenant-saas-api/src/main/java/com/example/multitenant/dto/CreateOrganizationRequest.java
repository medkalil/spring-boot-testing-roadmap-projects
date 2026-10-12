package com.example.multitenant.dto;

// POST /api/organizations
// { "name": "Acme" }
public record CreateOrganizationRequest(
        String name
) {
}