package com.example.todo.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure unit test (Surefire): the forward-only transition matrix. */
class TaskStatusTest {

    @Test
    void todoCanMoveForwardOnly() {
        assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.DONE)).isTrue();
        assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.TODO)).isFalse();
    }

    @Test
    void inProgressCanOnlyFinish() {
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.DONE)).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.TODO)).isFalse();
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void doneIsTerminal() {
        assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.TODO)).isFalse();
        assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.IN_PROGRESS)).isFalse();
        assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.DONE)).isFalse();
        assertThat(TaskStatus.DONE.canTransitionTo(null)).isFalse();
    }
}
