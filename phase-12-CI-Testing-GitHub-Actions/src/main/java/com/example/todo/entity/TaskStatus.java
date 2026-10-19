package com.example.todo.entity;

/**
 * Task lifecycle. Business rule (pure, unit-testable): transitions are
 * FORWARD ONLY - DONE is terminal, so a completed task can never be reopened.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    public boolean canTransitionTo(TaskStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case TODO -> target == IN_PROGRESS || target == DONE;
            case IN_PROGRESS -> target == DONE;
            case DONE -> false; // terminal state
        };
    }
}
