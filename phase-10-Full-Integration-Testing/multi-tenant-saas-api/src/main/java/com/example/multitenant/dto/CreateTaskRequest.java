package com.example.multitenant.dto;

// POST /api/projects/{projectId}/tasks
// { "title": "Implement login", "description": "Create authentication endpoint" }
public record CreateTaskRequest(
        String title,
        String description
) {
}