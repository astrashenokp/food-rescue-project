package com.example.foodrescue.security.web;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
