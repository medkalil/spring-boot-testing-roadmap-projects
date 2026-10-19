package com.example.todo.service;

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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status) {
        List<Task> tasks = status == null ? repository.findAll() : repository.findByStatus(status);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return TaskResponse.from(findOrThrow(id));
    }

    public TaskResponse create(TaskCreateRequest request) {
        String title = request.title() == null ? null : request.title().trim();
        if (title == null || title.isEmpty()) {
            throw new BadRequestException("title must not be blank");
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(request.description());
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        return TaskResponse.from(repository.save(task));
    }

    public TaskResponse update(Long id, TaskUpdateRequest request) {
        Task task = findOrThrow(id);
        if (task.getStatus() == TaskStatus.DONE) {
            throw new ConflictException("completed tasks cannot be edited");
        }
        String title = request.title() == null ? null : request.title().trim();
        if (title == null || title.isEmpty()) {
            throw new BadRequestException("title must not be blank");
        }
        task.setTitle(title);
        task.setDescription(request.description());
        return TaskResponse.from(repository.save(task));
    }

    public TaskResponse changeStatus(Long id, TaskStatusUpdateRequest request) {
        Task task = findOrThrow(id);
        if (!task.getStatus().canTransitionTo(request.status())) {
            throw new ConflictException(
                    "invalid status transition " + task.getStatus() + " -> " + request.status());
        }
        task.setStatus(request.status());
        return TaskResponse.from(repository.save(task));
    }

    public void delete(Long id) {
        Task task = findOrThrow(id);
        repository.delete(task);
    }

    private Task findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("task " + id + " was not found"));
    }
}
