package com.example.multitenant.dto;

// projectId is part of the response so tests can assert the ownership link
// task -> project -> organization against the database (section 57).
public record TaskResponse(
        Long id,
        String title,
        String description,
        Long projectId
) {
}