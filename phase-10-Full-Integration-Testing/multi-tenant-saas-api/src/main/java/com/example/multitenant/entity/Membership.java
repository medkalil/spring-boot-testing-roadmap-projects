package com.example.multitenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * "User X is (role) of Organization Y".
 *
 * The role is deliberately NOT stored on User: one user can be ADMIN of one
 * organization and MEMBER of another. This table - not the JWT - is what every
 * tenant authorization decision reads:
 *
 *   authenticated caller (JWT subject = email)
 *        -> membership for the organization that owns the resource
 *        -> membership missing  -> 404 (the resource "does not exist" for them)
 *        -> role too weak       -> 403 (they can see it, they may not do this)
 *
 * V3 guarantees (user_id, organization_id) is UNIQUE, so nobody can join the
 * same organization twice.
 */
@Entity
@Table(name = "memberships", uniqueConstraints = @UniqueConstraint(name = "uq_membership_user_org", columnNames = {"user_id", "organization_id"}))
public class Membership {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 20)
    private String role;

    protected Membership() {
    }

    public Membership(User user, Organization organization, String role) {
        this.user = user;
        this.organization = organization;
        this.role = role;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}