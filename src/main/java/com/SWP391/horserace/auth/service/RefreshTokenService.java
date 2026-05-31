package com.SWP391.horserace.auth.service;

import com.SWP391.horserace.auth.config.JwtProperties;
import com.SWP391.horserace.auth.entity.RefreshToken;
import com.SWP391.horserace.auth.repository.RefreshTokenRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Manages opaque, DB-stored refresh tokens with rotation and theft (reuse) detection.
 *
 * <p>Only a SHA-256 hash of each token is persisted. Issuing returns the raw token to the
 * client exactly once; on every refresh the presented token is revoked and a new one issued
 * (rotation). If an already-revoked token is presented, that signals theft — all of the
 * user's tokens are revoked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties props;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Result of a rotation: the user it belongs to plus the new raw refresh token to hand back. */
    public record Rotation(User user, String rawToken) {}

    /** Issue a fresh refresh token for a user and return the RAW token (shown to the client once). */
    @Transactional
    public String issue(User user, String userAgent) {
        String raw = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .expiresAt(OffsetDateTime.now().plusNanos(props.getRefreshTokenTtlMs() * 1_000_000L))
                .userAgent(truncate(userAgent))
                .build();
        repository.save(token);
        return raw;
    }

    /**
     * Validate the presented raw token, rotate it (revoke old, issue new), and return the
     * owning user + new raw token. Detects reuse of a revoked token.
     */
    @Transactional
    public Rotation rotate(String rawToken, String userAgent) {
        RefreshToken current = repository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (current.isRevoked()) {
            // Reuse of a revoked token => likely stolen. Burn all of this user's sessions.
            log.warn("Refresh token reuse detected for user {} — revoking all sessions",
                    current.getUser().getUserId());
            repository.revokeAllActiveForUser(current.getUser().getUserId(), OffsetDateTime.now());
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (current.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = current.getUser();
        String newRaw = generateRawToken();
        RefreshToken next = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(newRaw))
                .expiresAt(OffsetDateTime.now().plusNanos(props.getRefreshTokenTtlMs() * 1_000_000L))
                .userAgent(truncate(userAgent))
                .build();
        next = repository.save(next);

        current.setRevoked(true);
        current.setRevokedAt(OffsetDateTime.now());
        current.setReplacedByTokenId(next.getTokenId());
        repository.save(current);

        return new Rotation(user, newRaw);
    }

    /** Revoke a single token (logout). Idempotent — unknown/already-revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(sha256(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                token.setRevokedAt(OffsetDateTime.now());
                repository.save(token);
            }
        });
    }

    // ---- helpers ----

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String truncate(String ua) {
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
