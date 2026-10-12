package com.example.multitenant.repository;

import com.example.multitenant.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /** "Is this caller (still) a member of that organization?" - the tenant question. */
    Optional<Membership> findByUserEmailAndOrganizationId(String email, Long organizationId);

    boolean existsByUserEmailAndOrganizationId(String email, Long organizationId);
}