package com.example.multitenant.controller;

import com.example.multitenant.dto.AuthResponse;
import com.example.multitenant.dto.LoginRequest;
import com.example.multitenant.dto.RegisterRequest;
import com.example.multitenant.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The TWO endpoints that require no token. Everything behind /api/auth/**
 * is permitAll() in SecurityConfig; every other endpoint in the API forces
 * authentication and the JwtAuthenticationEntryPoint answers 401 otherwise
 * (section 41).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 201 on success. 409 if the email was already registered (section 58). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
    }

    /** 200 {accessToken, refreshToken} for valid credentials, else 401. */
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }
}