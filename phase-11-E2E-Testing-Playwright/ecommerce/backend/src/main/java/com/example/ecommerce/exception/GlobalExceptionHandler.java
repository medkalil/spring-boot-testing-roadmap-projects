package com.example.ecommerce.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Business exceptions -> HTTP statuses, so no controller contains try/catch:
 *
 *   ResourceNotFoundException    -> 404
 *   ConflictException            -> 409
 *   BadRequestException          -> 400
 *   DataIntegrityViolation       -> 409 (backstop for a unique-constraint race)
 *
 * The 401 path never reaches this class: JwtAuthenticationEntryPoint answers
 * before any controller runs, and login failures are thrown as
 * ResponseStatusException inside the service (also resolved off this class).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Backstop: a race between two requests can still hit the DB-level UNIQUE
     * constraint even if the exists-by checks both passed. It must not surface
     * as a 500. The services pre-check on purpose (nice 409 with message);
     * this keeps the guarantee for whatever slips through.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "That resource already exists");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", status.getReasonPhrase(),
                "message", message == null ? "" : message
        ));
    }
}