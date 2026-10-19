package com.example.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        @NotBlank(message = "title is required")
        @Size(min = 3, max = 100, message = "title must be between 3 and 100 characters")
        String title,
        @Size(max = 500, message = "description must be at most 500 characters")
        String description) {
}
