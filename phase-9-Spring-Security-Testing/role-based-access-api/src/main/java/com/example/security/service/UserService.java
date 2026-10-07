package com.example.security.service;

import com.example.security.dto.CreateUserRequest;
import com.example.security.dto.UserResponse;
import com.example.security.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    // In-memory store on purpose: Phase 9 is about the security filter chain,
    // not about persistence. Nothing here is ever consulted by a security test,
    // because @WebMvcTest replaces this bean with a Mockito mock.
    private final Map<Long, User> users = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(0);

    public UserService() {
        save(new User("john", "password", "USER"));
        save(new User("manager", "password", "MANAGER"));
        save(new User("admin", "password", "ADMIN"));
    }

    public List<UserResponse> getUsers() {
        return users.values().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse createUser(CreateUserRequest request) {
        User user = save(new User(
                request.username(),
                "password",
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