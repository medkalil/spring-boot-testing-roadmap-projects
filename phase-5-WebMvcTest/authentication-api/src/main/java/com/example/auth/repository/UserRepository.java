package com.example.auth.repository;

import com.example.auth.model.User;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    void saveResetToken(String email, String token);
    Optional<String> findEmailByResetToken(String token);
    void deleteResetTokenByEmail(String email);
}