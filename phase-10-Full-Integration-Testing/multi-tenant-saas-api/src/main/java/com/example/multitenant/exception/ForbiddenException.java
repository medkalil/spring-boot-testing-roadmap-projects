package com.example.multitenant.exception;

/**
 * The caller IS inside the right organization and CAN see the resource, but
 * their membership role may not perform this operation
 * (e.g. MEMBER deleting a project -> 403).
 *
 * Never thrown for cross-tenant access - that one is a 404.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}