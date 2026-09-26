package com.example.usermanagement.exception;

public class UserCannotDeleteHimselfException extends RuntimeException {
    public UserCannotDeleteHimselfException(String message) {
        super(message);
    }
}