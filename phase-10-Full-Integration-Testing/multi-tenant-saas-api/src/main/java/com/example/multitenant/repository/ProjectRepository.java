package com.example.multitenant.repository;

import com.example.multitenant.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * THE tenant-aware query of Phase 10:
     *
     *   SELECT * FROM projects p
     *   WHERE p.id = :projectId
     *     AND EXISTS (SELECT 1 FROM memberships m
     *                 WHERE m.user_id = <caller>
     *                   AND m.organization_id = p.organization_id);
     *
     * The tenant constraint is part of the WHERE clause, not a hopeful
     * if-statement in Java. A project that belongs to another organization
     * simply matches 0 rows -> Optional.empty() -> the service answers 404.
     *
     * This is stronger than findById(projectId): that method answers
     * "does the project exist?" while this one answers
     * "does the project exist FOR THIS CALLER?" - two different questions
     * (sections 6 and 45-46 of the phase document).
     */
    @Query("""
            select p from Project p
            where p.id = :projectId
              and exists (
                  select m from Membership m
                  where m.user.email = :email
                    and m.organization = p.organization
              )
            """)
    Optional<Project> findByIdForUser(@Param("projectId") Long projectId, @Param("email") String email);

    /** Plain tenant filter for callers that already know the organization. */
    Optional<Project> findByIdAndOrganizationId(Long projectId, Long organizationId);

    Long countByOrganizationId(Long organizationId);
}