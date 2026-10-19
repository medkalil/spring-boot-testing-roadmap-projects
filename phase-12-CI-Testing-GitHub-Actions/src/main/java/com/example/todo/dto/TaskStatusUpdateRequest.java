package com.example.todo.dto;

import com.example.todo.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(
        @NotNull(message = "status is required")
        TaskStatus status) {
}
