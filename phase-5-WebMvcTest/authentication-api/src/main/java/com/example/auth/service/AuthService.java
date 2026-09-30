package com.example.auth.service;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.ForgotPasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.ResetPasswordRequest;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.exception.InvalidResetTokenException;
import com.example.auth.exception.UserAlreadyExistsException;
import com.example.auth.exception.UserNotFoundException;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private static final String TOKEN_PREFIX = "token-";

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username already exists: " + request.getUsername());
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }
        User user = userRepository.save(
                new User(null, request.getUsername(), request.getEmail(), request.getPassword())
        );
        return new AuthResponse(user.getId(), user.getUsername(), TOKEN_PREFIX + user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!user.getPassword().equals(request.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return new AuthResponse(user.getId(), user.getUsername(), TOKEN_PREFIX + user.getId());
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        if (!userRepository.existsByEmail(request.getEmail())) {
            throw new UserNotFoundException("User not found with email: " + request.getEmail());
        }
        userRepository.saveResetToken(request.getEmail(), "reset-" + UUID.randomUUID());
    }

    public void resetPassword(ResetPasswordRequest request) {
        String email = userRepository.findEmailByResetToken(request.getToken())
                .orElseThrow(() -> new InvalidResetTokenException("Invalid reset token"));
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setPassword(request.getNewPassword());
            userRepository.save(user);
        });
        userRepository.deleteResetTokenByEmail(email);
    }
}