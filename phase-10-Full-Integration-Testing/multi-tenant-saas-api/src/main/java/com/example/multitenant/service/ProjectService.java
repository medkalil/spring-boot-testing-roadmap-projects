package com.example.multitenant.service;

import com.example.multitenant.dto.CreateProjectRequest;
import com.example.multitenant.dto.ProjectResponse;
import com.example.multitenant.entity.Membership;
import com.example.multitenant.entity.Project;
import com.example.multitenant.exception.BadRequestException;
import com.example.multitenant.exception.ForbiddenException;
import com.example.multitenant.exception.ResourceNotFoundException;
import com.example.multitenant.repository.MembershipRepository;
import com.example.multitenant.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MembershipRepository membershipRepository;

    public ProjectService(ProjectRepository projectRepository,
                          MembershipRepository membershipRepository) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * POST /api/organizations/{organizationId}/projects -> 201
     *
     * The membership row is fetched FIRST: without it the organization does not
     * exist as far as this caller is concerned (404), and only then is the
     * project written. A non-member never gets far enough to create anything.
     */
    @Transactional
    public ProjectResponse createProject(String email, Long organizationId, CreateProjectRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Project name is required");
        }

        Membership membership = membershipRepository
                .findByUserEmailAndOrganizationId(email, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization " + organizationId + " not found"));

        Project project = projectRepository.save(new Project(request.name(), membership.getOrganization()));

        return toResponse(project);
    }

    /**
     * GET /api/projects/{id} -> 200 / 404
     *
     * Uses findByIdForUser: the tenant condition is part of the SQL, so a
     * project of another organization matches 0 rows and the answer is the
     * same 404 as for an id that never existed. Nothing about Organization B
     * is observable from Organization A - not even its existence (sections 45-46).
     *
     * THIS is the method whose naive version (findById + orElseThrow) would be
     * the tenant isolation vulnerability of section 7.
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProject(String email, Long projectId) {
        Project project = projectRepository.findByIdForUser(projectId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));

        return toResponse(project);
    }

    /**
     * DELETE /api/projects/{id} -> 204 / 404 / 403
     *
     * Two independent questions, two different statuses:
     *   - may this caller SEE the project at all?  findByIdForUser -> else 404
     *     (cross-tenant and nonexistent are deliberately indistinguishable)
     *   - may this caller DELETE it?               membership role ADMIN -> else 403
     *     (inside the right tenant, wrong role - the resource IS visible)
     */
    @Transactional
    public void deleteProject(String email, Long projectId) {
        Project project = projectRepository.findByIdForUser(projectId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));

        Membership membership = membershipRepository
                .findByUserEmailAndOrganizationId(email, project.getOrganization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));

        if (!membership.isAdmin()) {
            throw new ForbiddenException("Only an ADMIN of this organization can delete projects");
        }

        projectRepository.delete(project);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getOrganization().getId()
        );
    }
}