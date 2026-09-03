package org.example.jwt.service;

import org.example.jwt.model.User;
import org.example.jwt.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final UserRepository userRepository;
    private final long expirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(UserRepository userRepository,
                               @Value("${jwt.refresh-expiration-ms}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalStateException("JWT refresh expiration must be greater than zero");
        }
        this.userRepository = userRepository;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setRefreshTokenHash(sha256(rawToken));
        user.setRefreshTokenExpiresAt(Instant.now().plusMillis(expirationMs));
        userRepository.save(user);
        return rawToken;
    }

    @Transactional
    public User rotate(String rawToken) {
        User user = userRepository.findByRefreshTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (user.getRefreshTokenExpiresAt() == null || user.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            revoke(user);
            throw new BadCredentialsException("Expired refresh token");
        }
        return user;
    }

    @Transactional
    public void revoke(User user) {
        user.setRefreshTokenHash(null);
        user.setRefreshTokenExpiresAt(null);
        userRepository.save(user);
    }

    private String sha256(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
