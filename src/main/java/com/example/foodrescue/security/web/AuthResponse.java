package com.example.foodrescue.common;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
