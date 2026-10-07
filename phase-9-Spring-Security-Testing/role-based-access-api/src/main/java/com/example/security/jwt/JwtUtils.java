package com.example.security.jwt;

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
 * Creates and validates the JWTs of this API.
 *
 * Token shape (HS256, signed with app.jwtSecret):
 *   header  { "alg": "HS256", "typ": "JWT" }
 *   payload {
 *     "sub": "admin",                          <- username, read by the filter
 *     "authorities": ["ROLE_ADMIN"],           <- roles, read by the filter
 *     "iat": ..., "exp": ...
 *   }
 *
 * IMPORTANT: the authorities live INSIDE the token. Nothing here consults the
 * database on every request, which is exactly what makes JWT stateless. The
 * trade-off: a role change only takes effect when a NEW token is issued.
 *
 * This class is a plain class, registered as a @Bean in SecurityConfig, so
 * that a @WebMvcTest that imports SecurityConfig alone gets everything it needs.
 */
public class JwtUtils {

    private final Key key;
    private final long expirationMs;

    public JwtUtils(String base64Secret, long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
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

    /**
     * Core minting method with explicit validity window. Production code always
     * uses the configured expirationMs; tests use this overload to build an
     * EXPIRED token on purpose without waiting an hour.
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
     * Throws on any problem - callers that need to know WHY (the entry point
     * answers "expired" vs "invalid") read the exception type:
     *   ExpiredJwtException, MalformedJwtException, SignatureException, ...
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