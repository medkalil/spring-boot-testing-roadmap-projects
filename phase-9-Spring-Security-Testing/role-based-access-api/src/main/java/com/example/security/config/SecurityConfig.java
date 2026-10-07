package com.example.security.config;

import com.example.security.jwt.AuthTokenFilter;
import com.example.security.jwt.JwtAuthenticationEntryPoint;
import com.example.security.jwt.JwtUtils;
import com.example.security.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /*
     * THE AUTHORIZATION MATRIX OF PHASE 9 (Layer 1 - what @WithMockUser tests)
     *
     *             GET    POST   DELETE
     *  anonymous   401    401    401
     *  USER        200    403    403
     *  MANAGER     200    201    403
     *  ADMIN       200    201    204
     *
     * The 401s now come from JwtAuthenticationEntryPoint instead of httpBasic:
     * one entry point, three flavours (anonymous / expired / invalid token),
     * all documented in its own class.
     *
     * AND WHAT THIS CONFIG ALSO ENABLES (Layer 3 - real JWT authentication)
     *
     *   login -> JWT -> protected endpoint
     *
     *   POST /api/auth/login is permitAll, otherwise no client could ever get
     *   a first token. Everything else still needs Authentication - either a
     *   real one built by AuthTokenFilter from a Bearer token, or a test one
     *   injected by @WithMockUser.
     *
     *   Everything the filter chain needs (JwtUtils, entry point, PasswordEncoder,
     *   AuthenticationManager) is declared as @Bean HERE and the AuthTokenFilter is
     *   created inside the chain, so a @WebMvcTest with a single
     *   @Import(SecurityConfig.class) gets a fully working chain:
     *   @WebMvcTest does not component-scan @Component/@Service classes, and
     *   reaching for several imports in every test class is how slices start
     *   breaking. (The filter is NOT a @Bean on purpose: a Filter bean would be
     *   auto-registered by Spring Boot as a servlet filter as well and would run
     *   twice - once outside and once inside the security chain.)
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtUtils jwtUtils,
                                            JwtAuthenticationEntryPoint entryPoint) throws Exception {

        http
            .authorizeHttpRequests(authorize -> authorize
                // Layer 3 starts here: login must be reachable anonymously.
                .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()

                .requestMatchers(HttpMethod.GET, "/api/users/**")
                    .hasAnyRole("USER", "MANAGER", "ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/users/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                .requestMatchers(HttpMethod.DELETE, "/api/users/**")
                    .hasRole("ADMIN")

                .anyRequest()
                    .authenticated()
            )

            // Stateless JWT: no session, no cookie. Every request authenticates
            // itself through the Authorization header. (The server-side session
            // is where a traditional login would keep the SecurityContext.)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // The one and only source of 401 responses.
            .exceptionHandling(exceptions ->
                    exceptions.authenticationEntryPoint(entryPoint))

            // BEFORE UsernamePasswordAuthenticationFilter: the JWT filter runs
            // first and, if it succeeds, there is nothing left for the form/basic
            // login machinery to do.
            .addFilterBefore(new AuthTokenFilter(jwtUtils), UsernamePasswordAuthenticationFilter.class)

            // LEFT ENABLED ON PURPOSE. POST/DELETE without .with(csrf()) in a test
            // fail with 403 from the CSRF filter - before authorization is ever
            // evaluated. That confusion is exactly what Phase 9 teaches. Bearer
            // tokens are not vulnerable to CSRF, so this is conservative, not wrong.
            .csrf(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Turns usernames + raw passwords from POST /api/auth/login into an
     * Authentication, or throws (BadCredentialsException -> 401).
     * DaoAuthenticationProvider = "look up UserDetails, compare password".
     */
    @Bean
    AuthenticationManager authenticationManager(UserService userService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * BCrypt: the stored form of "password" is a different hash every time it is
     * encoded, and login compares hashes instead of strings. Deliberately NOT
     * NoOpPasswordEncoder - that one only exists for tests and demos.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * @Bean instead of @Component so that importing SecurityConfig is enough
     * in a @WebMvcTest slice (which does not scan components).
     */
    @Bean
    JwtUtils jwtUtils(@Value("${app.jwtSecret}") String jwtSecret,
                      @Value("${app.jwtExpirationMs}") long jwtExpirationMs) {
        return new JwtUtils(jwtSecret, jwtExpirationMs);
    }

    @Bean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }
}