package com.example.ecommerce.service;

import com.example.ecommerce.dto.AuthResponse;
import com.example.ecommerce.dto.LoginRequest;
import com.example.ecommerce.dto.RegisterRequest;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.jwt.JwtUtils;
import com.example.ecommerce.repository.UserRepository;
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

    /** POST /api/auth/register -> 201. Duplicate email -> 409. */
    @Transactional
    public void register(RegisterRequest request) {
        if (isBlank(request.fullName()) || isBlank(request.email()) || isBlank(request.password())) {
            throw new BadRequestException("full name, email and password are required");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("A user with email " + request.email() + " already exists");
        }

        userRepository.save(new User(
                request.fullName().trim(),
                request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password())
        ));
    }

    /**
     * POST /api/auth/login -> 200 {accessToken, refreshToken}.
     * Unknown email AND wrong password both -> 401: the caller cannot tell
     * which of the two was wrong (no account enumeration).
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(),
                            request.password()
                    )
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