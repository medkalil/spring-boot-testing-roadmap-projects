package com.example.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.todo.dto.TaskCreateRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.dto.TaskStatusUpdateRequest;
import com.example.todo.dto.TaskUpdateRequest;
import com.example.todo.entity.Task;
import com.example.todo.entity.TaskStatus;
import com.example.todo.exception.BadRequestException;
import com.example.todo.exception.ConflictException;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.repository.TaskRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test (Surefire): business rules of the task lifecycle with a mocked
 * repository - no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository);
    }

    @Test
    void create_trimsTitleAndDefaultsStatusToTodo() {
        when(repository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        TaskResponse response = service.create(new TaskCreateRequest("   Ship it   ", null, null));

        assertThat(response.title()).isEqualTo("Ship it");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        verify(repository).save(any(Task.class));
    }

    @Test
    void create_blankTitle_throwsBadRequestAndNeverSaves() {
        assertThatThrownBy(() -> service.create(new TaskCreateRequest("    ", null, null)))
                .isInstanceOf(BadRequestException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void changeStatus_forwardTransition_savesNewStatus() {
        Task task = todoTask(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(task));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response =
                service.changeStatus(5L, new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS));

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(repository).save(task);
    }

    @Test
    void changeStatus_doneCannotMoveBackwards_throwsConflictAndNeverSaves() {
        Task task = todoTask(6L);
        task.setStatus(TaskStatus.DONE);
        when(repository.findById(6L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.changeStatus(6L, new TaskStatusUpdateRequest(TaskStatus.TODO)))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void changeStatus_unknownTask_throwsNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.changeStatus(42L, new TaskStatusUpdateRequest(TaskStatus.DONE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_completedTask_throwsConflictAndNeverSaves() {
        Task task = todoTask(7L);
        task.setStatus(TaskStatus.DONE);
        when(repository.findById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.update(7L, new TaskUpdateRequest("New title", null)))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void update_unknownTask_throwsNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L, new TaskUpdateRequest("New title", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_knownTask_returnsResponse() {
        Task task = todoTask(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(task));

        TaskResponse response = service.get(9L);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.title()).isEqualTo("Existing");
    }

    @Test
    void get_unknownTask_throwsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Task todoTask(Long id) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Existing");
        task.setStatus(TaskStatus.TODO);
        return task;
    }
}
