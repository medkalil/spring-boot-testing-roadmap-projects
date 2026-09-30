package com.example.auth.controller;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.ForgotPasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.ResetPasswordRequest;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.exception.UserAlreadyExistsException;
import com.example.auth.exception.UserNotFoundException;
import com.example.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

// @WebMvcTest auto-registers the @RestControllerAdvice (GlobalExceptionHandler),
// so mocked service exceptions are handled and assertable:
//   UserAlreadyExistsException  -> 409
//   InvalidCredentialsException -> 401
//   UserNotFoundException       -> 404
//   InvalidResetTokenException  -> 400
// No Need for the setUp
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private String asJsonString(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // Test Scenarios:
    // POST register	valid	201
    // POST register	missing fields	400
    // POST register	invalid email	400
    // POST register	duplicate user	409
    // POST login	valid	200
    // POST login	invalid credentials	401
    // POST login	invalid request	400
    // POST forgot-password	valid	204
    // POST forgot-password	user missing	404
    // POST reset-password	valid	204
    // POST reset-password	invalid token	appropriate error
    // POST reset-password	invalid request	400

    // Status:
    // 400: is validation dto
    // 401: is business logic
    
    // POST register	valid	201
    // POST register	missing fields	400
    // POST register	invalid email	400
    // POST register	duplicate user	409
    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        // valid request -> 201 + AuthResponse body
        @Test
        @DisplayName("Valid request -> 201 + AuthResponse body")
        void validRequest_shouldReturn201AndAuthResponse() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setEmail("kalil@gmail.com");
            request.setPassword("password");

            AuthResponse expectedResponse = new AuthResponse();
            expectedResponse.setUserId(1L);;
            expectedResponse.setUsername("testuser");
            expectedResponse.setToken("token-1");

            when(authService.register(any(RegisterRequest.class))).thenReturn(expectedResponse);

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(1L))
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.token").value("token-1"));
        }

        // validation: invalid body (blank username, bad email, short password) -> 400 
        @Test
        @DisplayName("Invalid body (missing field) -> 400")
        void invalidBody_missingField_shouldReturn400AndValidationMessage() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("kalil@gmail.com");
            request.setPassword("password");

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).register(request);
        }

        @Test
        @DisplayName("Invalid body (blank username) -> 400")
        void invalidBody_blankUsername_shouldReturn400AndValidationMessage() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(new RegisterRequest("", "kalil@gmail.com", "password"))))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).register(any(RegisterRequest.class));
        }
        
        @Test
        @DisplayName("Invalid body (bad email) -> 400")
        void invalidBody_badEmail_shouldReturn400AndValidationMessage() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(new RegisterRequest("tesUser", "kalil", "password"))))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).register(any(RegisterRequest.class));
        }
        
        @Test
        @DisplayName("Invalid body (short password) -> 400")
        void invalidBody_shortPassword_shouldReturn400AndValidationMessage() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(new RegisterRequest("tesUser", "kalil", "pa"))))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("Service throws UserAlreadyExistsException -> 409")
        void serviceThrowsUserNameAlreadyExistsException_shouldReturn409() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setEmail("kalil@gmail.com");
            request.setPassword("password");

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new UserAlreadyExistsException("Username already exists: testuser"));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isConflict());

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("Service throws UserAlreadyExistsException -> 409")
        void serviceThrowsUserEmailAlreadyExistsException_shouldReturn409() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testuser");
            request.setEmail("kalil@gmail.com");
            request.setPassword("password");

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new UserAlreadyExistsException("Email already exists: kalil@gmail.com"));

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isConflict());

            verify(authService).register(any(RegisterRequest.class));
        }

    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {
        // valid credentials -> 200 + AuthResponse body
        @Test
        @DisplayName("Valid credentials -> 200 + AuthResponse body")
        void validCredentials_shouldReturn200AndAuthResponse() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("kalil@gmail.com");
            request.setPassword("password");

            AuthResponse expectedResponse = new AuthResponse();
            expectedResponse.setUserId(1L);
            expectedResponse.setUsername("testuser");
            expectedResponse.setToken("token-1");

            when(authService.login(any(LoginRequest.class))).thenReturn(expectedResponse);

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isOk());

            verify(authService).login(any(LoginRequest.class));
        }

        // invalid body -> 400
        @Test
        @DisplayName("Invalid body (missing field) -> 400")
        void invalidBody_missingField_shouldReturn400AndValidationMessage() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setPassword("password");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).login(request);
        }

        // invalid credentials (service throws) -> 401
        @Test
        @DisplayName("Invalid credentials (service throws) -> 401")
        void invalidCredentials_shouldReturn401() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("kalil@gmail.com");
            request.setPassword("wrong-password");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new InvalidCredentialsException("Invalid email or password"));

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService).login(any(LoginRequest.class));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class ForgotPasswordTests {
        // valid email -> 204
        @Test
        @DisplayName("Valid email -> 204")
        void validEmail_shouldReturn204() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("kalil@gmail.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isNoContent());

            verify(authService).forgotPassword(request);
        }

        // unknown email (service throws) -> 404
        @Test
        @DisplayName("Unknown email (service throws) -> 404")
        void unknownEmail_shouldReturn404() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("kalil@gmail.com");

            doThrow(new UserNotFoundException("User not found with email: kalil@gmail.com"))
                .when(authService).forgotPassword(any(ForgotPasswordRequest.class));
            
            mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isNotFound());

            verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
        }

        // invalid body -> 400
        @Test
        @DisplayName("Invalid body (missing field) -> 400")
        void invalidBody_missingField_shouldReturn400AndValidationMessage() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();

            mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).forgotPassword(request);
        }
    }

    @Nested
    @DisplayName("POST /api/auth/reset-password")
    class ResetPasswordTests {
        // valid token -> 204
        @Test
        @DisplayName("Valid token -> 204")
        void validToken_shouldReturn204() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("token-1", "password-valid");
            
            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isNoContent());

            verify(authService).resetPassword(request);
        }
            
        // invalid token (service throws) -> 400
        @Test
        @DisplayName("Invalid token (service throws) -> 400")
        void invalidToken_shouldReturn400() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("", "password-valid");
            
            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).resetPassword(request);
        }
        
        // short new password -> 400
        @Test
        @DisplayName("short password (service throws) -> 400")
        void shortPassword_shouldReturn400() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("token-1", "pass");
            
            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).resetPassword(request);
        }
    }
}