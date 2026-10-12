package com.example.multitenant.exception;

/**
 * The resource does not exist OR the caller's tenant cannot see it.
 *
 * These two cases are deliberately indistinguishable from outside: returning
 * 404 for a cross-tenant project does not leak the fact that it exists
 * (section 20: "as far as you are concerned, this resource doesn't exist").
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}