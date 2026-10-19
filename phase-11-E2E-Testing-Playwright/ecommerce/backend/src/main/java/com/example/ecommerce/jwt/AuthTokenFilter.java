package com.example.ecommerce.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer eyJhbGciOi...
 *      -> verify signature + expiration
 *      -> put the email (subject) into the SecurityContext
 *
 * The filter only answers "who is calling". The e-commerce authorization
 * questions ("does this order belong to this user?") are answered later, in
 * the services, exactly like Phase 10.
 *
 * Failure behaviour: never write a response here. Invalid tokens park the
 * reason in JWT_ERROR_ATTRIBUTE and the request continues unauthenticated;
 * JwtAuthenticationEntryPoint is the single source of 401.
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    /** Request attribute used to tell the entry point WHY authentication failed. */
    public static final String JWT_ERROR_ATTRIBUTE = "jwt.error";

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    private final JwtUtils jwtUtils;

    public AuthTokenFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = parseJwt(request);

        if (jwt != null) {
            try {
                String email = jwtUtils.getUserNameFromJwtToken(jwt);
                List<String> authorities = jwtUtils.getAuthoritiesFromJwtToken(jwt);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                authorities.stream().map(SimpleGrantedAuthority::new).toList()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                logger.warn("Rejected JWT: {}", e.getMessage());
                request.setAttribute(JWT_ERROR_ATTRIBUTE, e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}