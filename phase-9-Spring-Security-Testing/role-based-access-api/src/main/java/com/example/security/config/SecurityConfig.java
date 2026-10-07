package com.example.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // The authorization matrix of Phase 9:
    //
    //             GET    POST   DELETE
    //  anonymous   401    401    401
    //  USER        200    403    403
    //  MANAGER     200    201    403
    //  ADMIN       200    201    204
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/users/**")
                    .hasAnyRole("USER", "MANAGER", "ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/users/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                .requestMatchers(HttpMethod.DELETE, "/api/users/**")
                    .hasRole("ADMIN")

                .anyRequest()
                    .authenticated()
            )

            // Without an AuthenticationEntryPoint, Spring Security's default answer
            // for an anonymous request would be 403, not the 401 the matrix expects.
            // httpBasic() supplies one that returns 401 + WWW-Authenticate.
            .httpBasic(Customizer.withDefaults())

            // LEFT ENABLED ON PURPOSE. POST/DELETE without .with(csrf()) in a test
            // fail with 403 from the CSRF filter - before authorization is ever
            // evaluated. That confusion is exactly what Phase 9.7 teaches.
            .csrf(Customizer.withDefaults());

        return http.build();
    }
}