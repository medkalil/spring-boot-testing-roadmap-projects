package com.example.security.service;

import com.example.security.dto.CreateUserRequest;
import com.example.security.dto.UserResponse;
import com.example.security.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService implements UserDetailsService {

    // In-memory store on purpose: Phase 9 is about the security filter chain,
    // not about persistence. Nothing here is ever consulted by a Layer 1
    // security test, because @WebMvcTest replaces this bean with a Mockito mock.
    //
    // Layer 3 tests DO use the real bean: login runs through
    // AuthenticationManager -> DaoAuthenticationProvider -> loadUserByUsername
    // below, and the password check is a real BCrypt comparison.
    private final Map<Long, User> users = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(0);

    private final PasswordEncoder passwordEncoder;

    public UserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;

        save(new User("john", encode("password"), "USER"));
        save(new User("manager", encode("password"), "MANAGER"));
        save(new User("admin", encode("password"), "ADMIN"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getUsername())
                        // passwords are stored BCrypt-hashed, never in clear text.
                        // The {noop}-style prefixes are NOT used: BCrypt hashes are
                        // self-describing ($2a$...) for the delegating encoder.
                        .password(user.getPassword())
                        // roles("ADMIN") -> authority "ROLE_ADMIN", which is exactly
                        // what .hasRole("ADMIN") in SecurityConfig looks for.
                        .roles(user.getRole())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No user named " + username));
    }

    public List<UserResponse> getUsers() {
        return users.values().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse createUser(CreateUserRequest request) {
        User user = save(new User(
                request.username(),
                encode("password"),
                "USER"
        ));

        return toResponse(user);
    }

    // Idempotent: deleting an id that does not exist is still a 204, so the
    // security matrix (DELETE /api/users/10 -> 204) stays about authorization
    // and is never mixed up with a business 404.
    public void deleteUser(Long id) {
        users.remove(id);
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    private String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private User save(User user) {
        long id = sequence.incrementAndGet();
        user.setId(id);
        users.put(id, user);
        return user;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }
}