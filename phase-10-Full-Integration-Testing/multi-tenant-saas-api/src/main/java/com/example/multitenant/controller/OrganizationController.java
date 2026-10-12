package com.example.multitenant.controller;

import com.example.multitenant.dto.AddMemberRequest;
import com.example.multitenant.dto.CreateOrganizationRequest;
import com.example.multitenant.dto.CreateProjectRequest;
import com.example.multitenant.dto.MemberResponse;
import com.example.multitenant.dto.OrganizationResponse;
import com.example.multitenant.dto.ProjectResponse;
import com.example.multitenant.service.OrganizationService;
import com.example.multitenant.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization-scoped endpoints.
 *
 * Authentication.getName() is the JWT subject = the caller's EMAIL, the same
 * value every service uses to resolve memberships and make tenant decisions.
 * No controller ever guesses "the caller" from the request body - a user can
 * only act on organizations THEY have a membership in.
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final ProjectService projectService;

    public OrganizationController(OrganizationService organizationService,
                                  ProjectService projectService) {
        this.organizationService = organizationService;
        this.projectService = projectService;
    }

    /**
     * 201. Two writes in one transaction: the organization row plus the
     * creator's ADMIN membership. Without that second row the creator would
     * not be able to see their own new organization (section 16).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse createOrganization(Authentication authentication,
                                                   @RequestBody CreateOrganizationRequest request) {
        return organizationService.createOrganization(authentication.getName(), request);
    }

    /** 200 for members of the organization, 404 for everyone else. */
    @GetMapping("/{organizationId}")
    public OrganizationResponse getOrganization(Authentication authentication,
                                                @PathVariable Long organizationId) {
        return organizationService.getOrganization(authentication.getName(), organizationId);
    }

    /** 201 when an ADMIN adds another registered user; 403 for non-ADMIN members; 409 for duplicate. */
    @PostMapping("/{organizationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(Authentication authentication,
                                    @PathVariable Long organizationId,
                                    @RequestBody AddMemberRequest request) {
        return organizationService.addMember(authentication.getName(), organizationId, request);
    }

    /** 201 for any member; 404 for non-members (the organization "does not exist"). */
    @PostMapping("/{organizationId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(Authentication authentication,
                                         @PathVariable Long organizationId,
                                         @RequestBody CreateProjectRequest request) {
        return projectService.createProject(authentication.getName(), organizationId, request);
    }
}