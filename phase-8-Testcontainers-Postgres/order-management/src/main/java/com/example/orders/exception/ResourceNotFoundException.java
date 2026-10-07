package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// The controller advice does NOT need an @ExceptionHandler entry for this class:
// @ResponseStatus already maps it to 404 everywhere, including inside
// MockMvc/RestAssured integration tests.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}