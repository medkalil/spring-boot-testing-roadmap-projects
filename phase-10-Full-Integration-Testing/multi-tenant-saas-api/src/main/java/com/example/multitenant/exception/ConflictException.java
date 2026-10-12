package com.example.multitenant.exception;

/** Unique constraint already satisfied: duplicate email, duplicate membership... */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}