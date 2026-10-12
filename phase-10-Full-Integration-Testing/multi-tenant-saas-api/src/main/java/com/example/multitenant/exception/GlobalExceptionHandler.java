package com.example.multitenant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * One place that turns the application's security/business exceptions into
 * HTTP statuses, so every controller stays free of try/catch:
 *
 *   ResourceNotFoundException -> 404   (missing OR other tenant)
 *   ForbiddenException         -> 403   (right tenant, wrong role)
 *   ConflictException          -> 409   (unique constraint / already exists)
 *   BadRequestException        -> 400   (invalid input)
 *
 * The 401 path never reaches this class: JwtAuthenticationEntryPoint answers
 * before any controller runs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
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
     * Backstop: a race between two requests (or any path that skips the
     * exists-by checks) still hits the DB-level UNIQUE constraint and must
     * not surface as a 500. The services pre-check on purpose - the nice 409
     * with a clear message - while this keeps the guarantee.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "That resource already exists");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", status.getReasonPhrase(),
                "message", message == null ? "" : message
        ));
    }
}