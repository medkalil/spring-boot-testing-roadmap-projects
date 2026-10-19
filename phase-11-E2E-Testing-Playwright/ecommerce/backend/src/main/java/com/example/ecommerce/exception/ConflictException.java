package com.example.ecommerce.exception;

/**
 * A business rule the request violates: duplicate email at registration,
 * insufficient stock, an unavailable product, re-adding a membership... ->
 * 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}