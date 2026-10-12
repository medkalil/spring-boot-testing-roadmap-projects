package com.example.multitenant.jwt;

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
 * Creates and validates the JWTs of this API (same design as Phase 9, with two
 * differences that matter for multi-tenancy):
 *
 *   1. subject = the user's EMAIL, because login identifies by email and every
 *      service resolves the caller's memberships from it.
 *   2. the authorities claim holds only the GLOBAL role (ROLE_USER).
 *      The per-organization roles (ADMIN of Acme, MEMBER of Globex) are NOT in
 *      the token: one token cannot express a different role per tenant, and
 *      roles must be re-read per request from the memberships table - which is
 *      also why a revoked membership takes effect immediately.
 *
 * Token shape (HS256, signed with app.jwtSecret):
 *   { "sub": "alice@example.com", "authorities": ["ROLE_USER"], "iat": ..., "exp": ... }
 *
 * Two lifetimes:
 *   accessToken   -> app.jwtExpirationMs        (sent as Authorization: Bearer)
 *   refreshToken  -> app.jwtRefreshExpirationMs (longer; refresh flow not in scope)
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
     * Throws on any problem - the entry point reads the exception type to tell
     * "expired" from "invalid" (ExpiredJwtException, MalformedJwtException,
     * SignatureException, ...).
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