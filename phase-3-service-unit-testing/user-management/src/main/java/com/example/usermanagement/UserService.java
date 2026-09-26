package com.example.usermanagement;

import com.example.usermanagement.exception.EmailAlreadyExistsException;
import com.example.usermanagement.exception.UsernameAlreadyExistsException;
import com.example.usermanagement.exception.UserCannotDeleteHimselfException;
import com.example.usermanagement.exception.UserNotActiveException;
import com.example.usermanagement.exception.UserNotFoundException;

import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + user.getEmail());
        }
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Username already exists: " + user.getUsername());
        }
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User updatedUser) {
        User existingUser = getUserById(id);
        existingUser.setUsername(updatedUser.getUsername());
        existingUser.setEmail(updatedUser.getEmail());
        return userRepository.save(existingUser);
    }

    public void deleteUser(Long id, Long currentUserId) {
        getUserById(id);
        if (id.equals(currentUserId)) {
            throw new UserCannotDeleteHimselfException("User cannot delete himself: " + id);
        }
        userRepository.deleteById(id);
    }

    public User login(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        Optional<User> user = userRepository.login(email);
        if(user.isEmpty()) {
            throw new UserNotFoundException("User not found with email: " + email);
        }
        if (!user.get().isActive()) {
            throw new UserNotActiveException("User is not active with email: " + email);
        }
        return user.get();
    }

}