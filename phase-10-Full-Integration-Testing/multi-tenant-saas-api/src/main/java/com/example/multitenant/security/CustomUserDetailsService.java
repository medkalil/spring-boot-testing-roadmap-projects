package com.example.multitenant.security;

import com.example.multitenant.entity.User;
import com.example.multitenant.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges OUR users table into Spring Security's login machinery.
 *
 * The "username" of Spring Security is this application's EMAIL:
 * login posts {"email": ...}, and the JWT subject is that same email, so the
 * value that travels Authentication -> token -> SecurityContext -> services is
 * always the email.
 *
 * The global authority ROLE_USER is the only role in the token. Per-organization
 * roles (ADMIN/MEMBER) are intentionally absent - they are read per request from
 * the memberships table, because one user has a different role in each tenant.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }
}