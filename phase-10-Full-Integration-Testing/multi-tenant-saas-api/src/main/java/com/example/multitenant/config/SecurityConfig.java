package com.example.multitenant.config;

import com.example.multitenant.jwt.AuthTokenFilter;
import com.example.multitenant.jwt.JwtAuthenticationEntryPoint;
import com.example.multitenant.jwt.JwtUtils;
import com.example.multitenant.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The whole API is stateless Bearer/JWT.
 *
 * CSRF is DISABLED - deliberately the opposite of Phase 9's teaching:
 * CSRF protects state-changing requests from a browser that is *authenticated
 * by a session cookie*. This API never uses cookies; the Authorization header
 * carries the token, which an attacker's webpage cannot set. With nothing
 * cookie-based, CSRF tokens would just be noise (document, section 41).
 *
 * Every request except POST /api/auth/** is authenticated. The filter never
 * runs on /api/auth/login on purpose: grant() trusts the principal, so it has
 * no business running on a request that is not yet authenticated.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtUtils jwtUtils,
                                                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(new AuthTokenFilter(jwtUtils),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Login goes through DaoAuthenticationProvider (loads UserDetails by email
     * and BCrypt-checks the password). The resulting principal is
     * authentication.getName(), the JWT subject, and later the
     * "who is calling?" value of every service call.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public JwtUtils jwtUtils(@Value("${app.jwtSecret}") String jwtSecret,
                             @Value("${app.jwtExpirationMs}") long jwtExpirationMs,
                             @Value("${app.jwtRefreshExpirationMs}") long jwtRefreshExpirationMs) {
        return new JwtUtils(jwtSecret, jwtExpirationMs, jwtRefreshExpirationMs);
    }

    @Bean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }
}