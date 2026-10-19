package com.example.ecommerce.exception;

/**
 * The resource does not exist OR it does not belong to the caller. In the
 * e-commerce rules, "another customer's order" is answered exactly like
 * "no such order": both get ResourceNotFoundException -> 404, and nobody can
 * tell which was the case.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}