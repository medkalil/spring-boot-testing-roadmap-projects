package com.example.usermanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.usermanagement.exception.EmailAlreadyExistsException;
import com.example.usermanagement.exception.UserCannotDeleteHimselfException;
import com.example.usermanagement.exception.UserNotActiveException;
import com.example.usermanagement.exception.UserNotFoundException;
import com.example.usermanagement.exception.UsernameAlreadyExistsException;

@ExtendWith (MockitoExtension.class)
class UserServiceTest {

    @Mock 
    private UserRepository userRepository;

    @InjectMocks 
    private UserService userService;

    private User user;

    @BeforeEach 
    public void setUp() {
        user = new User(1L, "kalil", "kalil@gmail.com", true);
    }

    // CREATE USER
    // ├── unique email + username → success
    // ├── duplicate email → exception
    // └── duplicate username → exception
    @Test 
    public void testCreateUser_Success() {
        User request = new User(null, "kalil", "kalil@gmail.com", true);
        when(userRepository.save(request)).thenReturn(user);
        User result = userService.createUser(request);
        assertEquals(user, result);
    }

    @Test 
    public void testCreateUser_UniqueEmail_ThrowsException() {
        User request = new User(null, "kalil", "kalil@gmail.com", true);
        when(userRepository.findByEmail("kalil@gmail.com")).thenReturn(Optional.of(user));
        EmailAlreadyExistsException ex = assertThrows(EmailAlreadyExistsException.class, () -> userService.createUser(request));
        assertEquals(ex.getMessage(), "Email already exists: kalil@gmail.com");
    }

    @Test 
    public void testCreateUser_UniqueUsername_ThrowsException() {
        User request = new User(null, "kalil", "kalil2@gmail.com", true);
        when(userRepository.findByEmail("kalil2@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("kalil")).thenReturn(Optional.of(user));
        UsernameAlreadyExistsException ex = assertThrows(UsernameAlreadyExistsException.class, () -> userService.createUser(request));
        assertEquals(ex.getMessage(), "Username already exists: kalil");
    }

    // GET USER
    // ├── user exists → return user
    // └── user doesn't exist → exception
    @Test 
    public void testGetUserById_Exists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        User result = userService.getUserById(1L);
        assertEquals(user, result);
    }
    
    @Test 
    public void testGetUserById_ExistsNotFoundThrowsException() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> userService.getUserById(2L));
        assertEquals(ex.getMessage(), "User not found with id: 2");
    }

    // DELETE USER
    // ├── delete another user → success
    // ├── user doesn't exist → exception
    // └── delete yourself → exception
    @Test
    public void testDeleteUser_Success() {
        Long currentId = 3L;
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        userService.deleteUser(1L, currentId);
        verify(userRepository).deleteById(1L);
    }

    @Test
    public void testDeleteUser_UserNotFoundThrowsException() {
        Long currentId = 3L;
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> userService.deleteUser(2L, currentId));
        assertEquals(ex.getMessage(), "User not found with id: 2"); 
    }

    @Test
    public void testDeleteUser_CannotDeleteHimselfThrowsException() {
        Long currentId = 1L;
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UserCannotDeleteHimselfException ex = assertThrows(UserCannotDeleteHimselfException.class, () -> userService.deleteUser(1L, currentId));
        assertEquals(ex.getMessage(), "User cannot delete himself: 1"); 
    }


    // LOGIN
    // ├── active user → success
    // └── inactive user → exception
    @Test 
    public void testLogin_Success() {
        when(userRepository.login("kalil@gmail.com")).thenReturn(Optional.of(user));
        User result = userService.login("kalil@gmail.com");
        assertEquals(user, result);
    }

    @Test 
    public void testLogin_UserNotFoundThrowsException() {
        when(userRepository.login("kalil2@gmail.com")).thenReturn(Optional.empty());
        UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> userService.login("kalil2@gmail.com"));
        assertEquals(ex.getMessage(), "User not found with email: kalil2@gmail.com");
    }    

    @Test 
    public void testLogin_UserNotActiveThrowsException() {
        User inactiveUser = new User(1L, "kalil", "kalil@gmail.com", false);
        when(userRepository.login("kalil@gmail.com")).thenReturn(Optional.of(inactiveUser));
        UserNotActiveException ex = assertThrows(UserNotActiveException.class, () -> userService.login("kalil@gmail.com"));
        assertEquals(ex.getMessage(), "User is not active with email: kalil@gmail.com");
        
    }

}