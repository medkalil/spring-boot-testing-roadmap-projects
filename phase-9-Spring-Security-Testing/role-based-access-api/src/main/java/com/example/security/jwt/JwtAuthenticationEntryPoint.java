package com.example.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * The single place that produces 401 Unauthorized.
 *
 * Three flavours, all status 401, distinguishable in tests via the body and the
 * WWW-Authenticate header:
 *
 *   1. no Authorization header at all (anonymous)
 *        -> {"error":"unauthenticated", ...}
 *   2. a token that is present but EXPIRED
 *        -> WWW-Authenticate: Bearer error="invalid_token", error_description="The token is expired"
 *   3. a token that is present but malformed / wrongly signed
 *        -> WWW-Authenticate: Bearer error="invalid_token"
 *
 * The difference between 1 and 2/3 is information the CLIENT can act on:
 * "log in first" vs "your token is no longer good, send a fresh one".
 * AuthTokenFilter parks the reason in JWT_ERROR_ATTRIBUTE so this class can tell.
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object jwtError = request.getAttribute(AuthTokenFilter.JWT_ERROR_ATTRIBUTE);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (jwtError instanceof ExpiredJwtException) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The token is expired\"");
            response.getWriter().write(
                    "{\"error\":\"invalid_token\",\"message\":\"The token is expired\"}");
        } else if (jwtError != null) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            response.getWriter().write("{\"error\":\"invalid_token\",\"message\":\"The token is invalid\"}");
        } else {
            response.getWriter().write(
                    "{\"error\":\"unauthenticated\",\"message\":\"Authentication is required to access this resource\"}");
        }
    }
}