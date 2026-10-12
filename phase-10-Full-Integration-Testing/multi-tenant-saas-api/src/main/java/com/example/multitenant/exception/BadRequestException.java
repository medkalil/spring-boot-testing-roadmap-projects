package com.example.multitenant.exception;

/** The request body is syntactically fine but semantically invalid (blank name...). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}