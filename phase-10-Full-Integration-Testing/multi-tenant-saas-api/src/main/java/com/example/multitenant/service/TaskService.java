package com.example.multitenant.service;

import com.example.multitenant.dto.CreateTaskRequest;
import com.example.multitenant.dto.TaskResponse;
import com.example.multitenant.entity.Project;
import com.example.multitenant.entity.Task;
import com.example.multitenant.exception.BadRequestException;
import com.example.multitenant.exception.ResourceNotFoundException;
import com.example.multitenant.repository.ProjectRepository;
import com.example.multitenant.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * POST /api/projects/{projectId}/tasks -> 201 / 400 / 404
     *
     * Ownership chain: task -> project -> organization.
     * findByIdForUser proves the project belongs to an organization the caller
     * is a member of; only then is the task written. A caller from another
     * tenant sees 404 and creates nothing - and because service methods are
     * transactional, a failure anywhere in this method leaves no partial rows.
     *
     * Both ADMIN and MEMBER may create tasks: operation-level restrictions
     * (level 5) start with destructive operations such as DELETE project.
     */
    @Transactional
    public TaskResponse createTask(String email, Long projectId, CreateTaskRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("Task title is required");
        }

        Project project = projectRepository.findByIdForUser(projectId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));

        Task task = taskRepository.save(new Task(request.title(), request.description(), project));

        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getProject().getId()
        );
    }
}