package com.example.ecommerce.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Creates and validates the JWTs of this API (same design as Phases 9-10):
 *
 *   subject     = the user's EMAIL (login identifies by email).
 *   authorities = ["ROLE_USER"] only - there is no role hierarchy in this app,
 *                 every authorization question is "is the bearer the owner?"
 *
 * Two lifetimes:
 *   accessToken  -> app.jwtExpirationMs        (sent as Authorization: Bearer)
 *   refreshToken -> app.jwtRefreshExpirationMs (longer; refresh flow not in scope)
 */
public class JwtUtils {

    private final Key key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtUtils(String base64Secret, long expirationMs, long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Login path: subject and authorities come from the authenticated principal. */
    public String generateJwtToken(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return generateJwtToken(
                authentication.getName(),
                authorities.stream().map(GrantedAuthority::getAuthority).toList(),
                new Date(),
                new Date(System.currentTimeMillis() + expirationMs)
        );
    }

    /** Refresh token: same claims, longer lifetime. */
    public String generateRefreshToken(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return generateJwtToken(
                authentication.getName(),
                authorities.stream().map(GrantedAuthority::getAuthority).toList(),
                new Date(),
                new Date(System.currentTimeMillis() + refreshExpirationMs)
        );
    }

    /**
     * Core minting method with explicit validity window. Production code uses
     * the configured expirations; tests use this overload to build an EXPIRED
     * token on purpose without waiting an hour.
     */
    public String generateJwtToken(String username,
                                   Collection<String> authorities,
                                   Date issuedAt,
                                   Date expiration) {
        return Jwts.builder()
                .setSubject(username)
                .claim("authorities", authorities)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * Parses AND verifies signature and expiration.
     * Throws on any problem - JwtAuthenticationEntryPoint reads the exception
     * type to tell "expired" from "invalid".
     */
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getUserNameFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromJwtToken(String token) {
        return parseClaims(token).get("authorities", List.class);
    }

    /** Convenience boolean form: true = valid, false = expired/forged/malformed. */
    public boolean validateJwtToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}