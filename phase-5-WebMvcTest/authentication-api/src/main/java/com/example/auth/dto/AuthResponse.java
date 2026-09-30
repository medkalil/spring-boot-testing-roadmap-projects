package com.example.auth.dto;

public class AuthResponse {

    private Long userId;
    private String username;
    private String token;

    public AuthResponse(){ }
    
    public AuthResponse(
            Long userId,
            String username,
            String token
    ) {
        this.userId = userId;
        this.username = username;
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setToken(String token) {
        this.token = token;
    }
}