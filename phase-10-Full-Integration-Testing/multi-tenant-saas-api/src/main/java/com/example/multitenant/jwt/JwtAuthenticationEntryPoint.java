package com.example.multitenant.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * The single source of 401 Unauthorized - the "authentication failed" answer:
 *
 *   no Authorization header          -> {"error":"unauthenticated", ...}
 *   expired token                    -> WWW-Authenticate ... "The token is expired"
 *   malformed / wrongly signed token -> WWW-Authenticate ... invalid_token
 *
 * Deliberately different from the tenant answers:
 *   401  = authentication failed  (this file)
 *   403  = authenticated, right tenant, wrong role        (service layer)
 *   404  = authenticated, but the resource is not visible
 *          to this tenant (cross-tenant is treated as "does not exist")
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
            response.setHeader("WWW-Authenticate",
                    "Bearer error=\"invalid_token\", error_description=\"The token is expired\"");
            response.getWriter().write(
                    "{\"error\":\"invalid_token\",\"message\":\"The token is expired\"}");
        } else if (jwtError != null) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            response.getWriter().write(
                    "{\"error\":\"invalid_token\",\"message\":\"The token is invalid\"}");
        } else {
            response.getWriter().write(
                    "{\"error\":\"unauthenticated\",\"message\":\"Authentication is required to access this resource\"}");
        }
    }
}