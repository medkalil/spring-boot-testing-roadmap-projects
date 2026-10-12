package com.example.multitenant.service;

import com.example.multitenant.dto.AddMemberRequest;
import com.example.multitenant.dto.CreateOrganizationRequest;
import com.example.multitenant.dto.MemberResponse;
import com.example.multitenant.dto.OrganizationResponse;
import com.example.multitenant.entity.Membership;
import com.example.multitenant.entity.Organization;
import com.example.multitenant.entity.User;
import com.example.multitenant.exception.BadRequestException;
import com.example.multitenant.exception.ConflictException;
import com.example.multitenant.exception.ForbiddenException;
import com.example.multitenant.exception.ResourceNotFoundException;
import com.example.multitenant.repository.MembershipRepository;
import com.example.multitenant.repository.OrganizationRepository;
import com.example.multitenant.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/organizations -> 201
     *
     * Two writes in ONE transaction, on purpose: the organization AND the
     * creator's ADMIN membership. Creating an organization is not merely
     * organizationRepository.save(...) - it establishes the authorization
     * relationship everything else is built on (section 16).
     */
    @Transactional
    public OrganizationResponse createOrganization(String email, CreateOrganizationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Organization name is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));

        Organization organization = organizationRepository.save(new Organization(request.name()));
        membershipRepository.save(new Membership(user, organization, Membership.ROLE_ADMIN));

        return new OrganizationResponse(organization.getId(), organization.getName());
    }

    /**
     * GET /api/organizations/{id} -> 200 for members, 404 for everybody else.
     * The membership lookup IS the tenant check: no membership, no visibility.
     */
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(String email, Long organizationId) {
        Membership membership = requireMembership(email, organizationId);
        Organization organization = membership.getOrganization();
        return new OrganizationResponse(organization.getId(), organization.getName());
    }

    /**
     * POST /api/organizations/{id}/members -> 201
     *
     * Three checks, in order - the five authorization levels of section 43:
     *   1. membership exists?        -> else 404 (wrong tenant: not visible)
     *   2. membership role == ADMIN? -> else 403 (right tenant, wrong role)
     *   3. target user exists and is not already a member -> 404 / 409
     */
    @Transactional
    public MemberResponse addMember(String email, Long organizationId, AddMemberRequest request) {
        Membership caller = requireMembership(email, organizationId); // if not same tenant/org throws

        if (!caller.isAdmin()) { // autorization must be ROLE_ADMIN
            throw new ForbiddenException("Only an ADMIN of this organization can add members");
        }

        // Added user role must be ADMIN or MEMBER
        if (!Membership.ROLE_ADMIN.equals(request.role()) && !Membership.ROLE_MEMBER.equals(request.role())) {
            throw new BadRequestException("Role must be ADMIN or MEMBER");
        }

        User target = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user with email " + request.email()));

        // no deplicate membership as unique(userId, organizationId) from db & entity
        if (membershipRepository.existsByUserEmailAndOrganizationId(target.getEmail(), organizationId)) {
            throw new ConflictException("User is already a member of this organization");
        }

        Membership saved = membershipRepository.save(
                new Membership(target, caller.getOrganization(), request.role()));

        return new MemberResponse(target.getId(), organizationId, saved.getRole());
    }

    private Membership requireMembership(String email, Long organizationId) {
        return membershipRepository.findByUserEmailAndOrganizationId(email, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization " + organizationId + " not found"));
    }
}