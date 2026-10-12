package com.example.multitenant.service;

import com.example.multitenant.dto.AuthResponse;
import com.example.multitenant.dto.LoginRequest;
import com.example.multitenant.dto.RegisterRequest;
import com.example.multitenant.entity.User;
import com.example.multitenant.jwt.JwtUtils;
import com.example.multitenant.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    /**
     * POST /api/auth/register -> 201
     * Persists the user; nothing else. Organization membership is created later,
     * by POST /api/organizations (the creator becomes ADMIN of THAT organization).
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (isBlank(request.username()) || isBlank(request.email()) || isBlank(request.password())) {
            throw new com.example.multitenant.exception.BadRequestException(
                    "username, email and password are required");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new com.example.multitenant.exception.ConflictException(
                    "A user with email " + request.email() + " already exists");
        }

        userRepository.save(new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password())
        ));
    }

    /**
     * POST /api/auth/login -> 200 {accessToken, refreshToken}
     * 401 for unknown email AND for wrong password - the caller cannot tell
     * which of the two was wrong (no user enumeration).
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials", e);
        }

        return new AuthResponse(
                jwtUtils.generateJwtToken(authentication),
                jwtUtils.generateRefreshToken(authentication)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}