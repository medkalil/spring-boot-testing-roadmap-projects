package com.example.security.jwt;

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
 * The heart of Layer 3 authentication:
 *
 *   Authorization: Bearer eyJhbGciOi...
 *        -> parse + verify the token (signature, expiration)
 *        -> put username + authorities into the SecurityContext
 *        -> the authorization rules of SecurityConfig then see "admin" with ROLE_ADMIN
 *
 * Behaviour that the tests are about:
 *   - no Authorization header      -> nothing happens: the request stays
 *                                     anonymous and the entry point answers 401
 *   - valid token                  -> SecurityContext is populated
 *   - expired / forged / malformed -> ALSO nothing happens, but the exception is
 *                                     parked in the request attribute
 *                                     JWT_ERROR_ATTRIBUTE so the entry point can
 *                                     answer 401 with a more precise
 *                                     WWW-Authenticate header
 *
 * The filter never writes an error response itself. The entry point is the
 * single place that produces 401 - that keeps "why am I getting 401" in one class.
 *
 * Note what it does NOT do: it never looks at the database. A token for a user
 * that was deleted a second ago still authenticates until it expires.
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
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                List<String> authorities = jwtUtils.getAuthoritiesFromJwtToken(jwt); //will be : [ROLE_ADMIN, ROLE_MANAGER, ROLE_USER]

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                authorities.stream().map(SimpleGrantedAuthority::new).toList()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Do not fail here: park the reason and let the request continue
                // unauthenticated. The entry point turns it into the 401.
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