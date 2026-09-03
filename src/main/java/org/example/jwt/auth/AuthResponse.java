package org.example.jwt.auth;

public record AuthResponse(String accessToken, String refreshToken, String tokenType) {
}
