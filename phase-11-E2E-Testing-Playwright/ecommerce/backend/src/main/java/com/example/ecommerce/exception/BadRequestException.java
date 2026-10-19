package com.example.ecommerce.exception;

/** A required field missing or empty (blank shipping address, quantity 0...). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}