package com.example.multitenant.controller;

import com.example.multitenant.dto.CreateTaskRequest;
import com.example.multitenant.dto.ProjectResponse;
import com.example.multitenant.dto.TaskResponse;
import com.example.multitenant.service.ProjectService;
import com.example.multitenant.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project-scoped endpoints.
 *
 * GET and DELETE go through ProjectRepository.findByIdForUser - the tenant
 * condition is in the SQL itself, so a project of another organization
 * matches 0 rows and is answered as 404, never exposed (sections 6, 45-46).
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;

    public ProjectController(ProjectService projectService, TaskService taskService) {
        this.projectService = projectService;
        this.taskService = taskService;
    }

    /** 200 for members of the owning organization, 404 otherwise. */
    @GetMapping("/{projectId}")
    public ProjectResponse getProject(Authentication authentication,
                                      @PathVariable Long projectId) {
        return projectService.getProject(authentication.getName(), projectId);
    }

    /**
     * 204 for an ADMIN of the owning organization,
     * 403 for a MEMBER of the owning organization,
     * 404 for anyone outside it (cross-tenant, or project does not exist).
     */
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(Authentication authentication,
                              @PathVariable Long projectId) {
        projectService.deleteProject(authentication.getName(), projectId);
    }

    /** 201 for any member of the owning organization, 404 otherwise. */
    @PostMapping("/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(Authentication authentication,
                                   @PathVariable Long projectId,
                                   @RequestBody CreateTaskRequest request) {
        return taskService.createTask(authentication.getName(), projectId, request);
    }
}