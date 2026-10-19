package com.example.ecommerce.config;

import com.example.ecommerce.jwt.AuthTokenFilter;
import com.example.ecommerce.jwt.JwtAuthenticationEntryPoint;
import com.example.ecommerce.jwt.JwtUtils;
import com.example.ecommerce.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
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
 * Stateless Bearer/JWT API (as in Phases 9-10):
 *   - /api/auth/**      permit all   (register + login)
 *   - /api/products/**  permit all   (the public catalog)
 *   - everything else                (cart, orders) requires a valid token
 *
 * CSRF is disabled: nothing here is cookie-based. The React app keeps the token
 * in localStorage and sends Authorization: Bearer - a cross-site request
 * cannot forge that header.
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
                        .requestMatchers("/api/products/**").permitAll()
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

    @Bean
    public AuthenticationManager authenticationManager(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
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