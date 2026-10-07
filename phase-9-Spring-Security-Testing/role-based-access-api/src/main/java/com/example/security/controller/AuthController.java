package com.example.security.controller;

import com.example.security.dto.JwtResponse;
import com.example.security.dto.LoginRequest;
import com.example.security.jwt.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The first step of the Layer 3 flow:
 *
 *   POST /api/auth/login   {"username":"john","password":"password"}
 *        -> AuthenticationManager checks credentials (BCrypt + UserDetailsService)
 *        -> 200 {"token":"eyJ...","type":"Bearer","username":"john","role":"USER"}
 *
 *   GET /api/users  Authorization: Bearer eyJ...
 *        -> AuthTokenFilter reads the token, no password check any more
 *
 * The endpoint is permitAll in SecurityConfig - otherwise anonymous callers
 * could never obtain a first token. It is still a POST, so while CSRF is
 * enabled a test MUST send .with(csrf()) even for the login call.
 *
 * Bad credentials are deliberately turned into 401 (not 500 and not 403):
 * the caller supplied an authentication attempt and it failed.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public JwtResponse login(@RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException e) {
            // BadCredentialsException covers both "unknown user" (hidden behind
            // BadCredentials by DaoAuthenticationProvider) and "wrong password".
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials", e);
        }

        String token = jwtUtils.generateJwtToken(authentication);

        //role: ADMIN, so authority -> ROLE_ADMIN. and that why we remove the "ROLE_" prefix.
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse("");

        return new JwtResponse(token, authentication.getName(), role);
    }
}