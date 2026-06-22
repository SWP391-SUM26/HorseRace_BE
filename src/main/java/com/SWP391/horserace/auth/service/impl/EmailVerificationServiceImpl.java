package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.entity.EmailVerificationToken;
import com.SWP391.horserace.auth.repository.EmailVerificationTokenRepository;
import com.SWP391.horserace.auth.service.EmailService;
import com.SWP391.horserace.auth.service.EmailVerificationService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Implements the standalone email-verification lifecycle.
 *
 * <ol>
 *   <li>{@link #requestVerification}: generates a 6-digit code, hashes it, stores it,
 *       and emails it (best-effort).</li>
 *   <li>{@link #verifyEmail}: verifies the hashed code, flags the user's email as
 *       verified, and marks the token used.</li>
 * </ol>
 *
 * <p>Mirrors {@code PasswordResetServiceImpl} for code generation, SHA-256 hashing,
 * expiry, and the {@link EmailService} mail component. Does NOT touch registration
 * or login behaviour — {@code emailVerified} is purely a flag.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.email-verification.code-ttl-minutes:15}")
    private int codeTtlMinutes;

    @Override
    @Transactional
    public void requestVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmailAndDeletedFalse(email);

        if (userOpt.isEmpty()) {
            // Silently succeed to prevent user enumeration — attacker cannot know if email exists
            log.debug("Email-verification requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();

        // Generate & store a hashed 6-digit code
        String code = generateSixDigitCode();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(codeTtlMinutes))
                .build();
        verificationTokenRepository.save(token);

        // Send email — best-effort: an SMTP failure in dev must NOT fail the request
        try {
            emailService.sendVerificationCode(email, code);
        } catch (Exception e) {
            log.error("Failed to send email-verification code to {} (continuing)", email, e);
        }

        log.info("Email verification code issued for user {}", user.getUserId());
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String code) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        EmailVerificationToken token = verificationTokenRepository
                .findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        if (!sha256(code).equals(token.getCodeHash())) {
            throw new AppException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        if (token.isExpired()) {
            throw new AppException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        // Flag the user's email as verified
        user.setEmailVerified(true);
        userRepository.save(user);

        // Mark token as used
        token.setUsed(true);
        token.setUsedAt(OffsetDateTime.now());
        verificationTokenRepository.save(token);

        log.info("Email verified for user {}", user.getUserId());
    }

    // ---- helpers ----

    private String generateSixDigitCode() {
        int code = 100_000 + secureRandom.nextInt(900_000); // 100000 – 999999
        return String.valueOf(code);
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
}
