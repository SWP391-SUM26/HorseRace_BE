package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.entity.PasswordResetToken;
import com.SWP391.horserace.auth.repository.PasswordResetTokenRepository;
import com.SWP391.horserace.auth.repository.RefreshTokenRepository;
import com.SWP391.horserace.auth.service.EmailService;
import com.SWP391.horserace.auth.service.PasswordResetService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Implements the forgot-password / reset-password lifecycle.
 *
 * <ol>
 *   <li>{@link #forgotPassword}: generates a 6-digit code, hashes it, stores it, and emails it.</li>
 *   <li>{@link #resendCode}: invalidates old codes and issues a fresh one.</li>
 *   <li>{@link #verifyCode}: validates the code without consuming it.</li>
 *   <li>{@link #resetPassword}: verifies the hashed code, updates the password, revokes sessions.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Figma password rules: 8+ characters, at least 1 number, at least 1 symbol. */
    private static final Pattern HAS_DIGIT  = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_SYMBOL = Pattern.compile(".*[^a-zA-Z0-9].*");
    private static final int     MIN_LENGTH = 8;

    @Value("${app.password-reset.code-ttl-minutes:15}")
    private int codeTtlMinutes;

    @Value("${app.password-reset.cooldown-seconds:60}")
    private int cooldownSeconds;

    @Override
    @Transactional
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmailAndDeletedFalse(email);

        if (userOpt.isEmpty()) {
            // Silently succeed to prevent user enumeration — attacker cannot know if email exists
            log.debug("Forgot-password requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();

        // Cooldown check: prevent spamming reset codes
        OffsetDateTime cooldownCutoff = OffsetDateTime.now().minusSeconds(cooldownSeconds);
        long recentCount = resetTokenRepository.countByUser_UserIdAndCreatedAtAfter(
                user.getUserId(), cooldownCutoff);
        if (recentCount > 0) {
            throw new AppException(ErrorCode.RESET_CODE_COOLDOWN);
        }

        // Generate 6-digit code
        String code = generateSixDigitCode();

        // Store hashed code
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(codeTtlMinutes))
                .build();
        resetTokenRepository.save(token);

        // Send email
        emailService.sendResetCode(email, code);

        log.info("Password reset code issued for user {}", user.getUserId());
    }

    @Override
    @Transactional
    public void resendCode(String email) {
        Optional<User> userOpt = userRepository.findByEmailAndDeletedFalse(email);

        if (userOpt.isEmpty()) {
            log.debug("Resend-code requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();

        // Cooldown check
        OffsetDateTime cooldownCutoff = OffsetDateTime.now().minusSeconds(cooldownSeconds);
        long recentCount = resetTokenRepository.countByUser_UserIdAndCreatedAtAfter(
                user.getUserId(), cooldownCutoff);
        if (recentCount > 0) {
            throw new AppException(ErrorCode.RESET_CODE_COOLDOWN);
        }

        // Invalidate all previous unused codes for this user
        invalidateOldCodes(user);

        // Generate & store a fresh code
        String code = generateSixDigitCode();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(codeTtlMinutes))
                .build();
        resetTokenRepository.save(token);

        // Send email
        emailService.sendResetCode(email, code);

        log.info("Password reset code re-sent for user {}", user.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyCode(String email, String code) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.RESET_CODE_INVALID));

        PasswordResetToken token = resetTokenRepository
                .findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.RESET_CODE_INVALID));

        if (!sha256(code).equals(token.getCodeHash())) {
            throw new AppException(ErrorCode.RESET_CODE_INVALID);
        }

        if (token.isExpired()) {
            throw new AppException(ErrorCode.RESET_CODE_INVALID);
        }

        // Code is valid — do NOT mark as used; that happens in resetPassword.
        log.debug("Verification code validated for user {}", user.getUserId());
    }

    @Override
    @Transactional
    public void resetPassword(String email, String code, String newPassword, String confirmPassword) {
        // Validate passwords match
        if (!newPassword.equals(confirmPassword)) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // Validate password strength (Figma: 8+ characters, 1 number, 1 symbol)
        validatePasswordStrength(newPassword);

        // Find user
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.RESET_CODE_INVALID));

        // Find the most recent unused token for this user
        PasswordResetToken token = resetTokenRepository
                .findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.RESET_CODE_INVALID));

        // Verify code hash matches
        if (!sha256(code).equals(token.getCodeHash())) {
            throw new AppException(ErrorCode.RESET_CODE_INVALID);
        }

        // Check expiry
        if (token.isExpired()) {
            throw new AppException(ErrorCode.RESET_CODE_INVALID);
        }

        // Mark token as used
        token.setUsed(true);
        token.setUsedAt(OffsetDateTime.now());
        resetTokenRepository.save(token);

        // Update password (bcrypt-encoded)
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all refresh tokens — force re-login on all devices
        refreshTokenRepository.revokeAllActiveForUser(user.getUserId(), OffsetDateTime.now());

        log.info("Password reset completed for user {}", user.getUserId());
    }

    // ---- helpers ----

    private String generateSixDigitCode() {
        int code = 100_000 + secureRandom.nextInt(900_000); // 100000 – 999999
        return String.valueOf(code);
    }

    /** Invalidate all previously unused reset codes for a user (used on resend). */
    private void invalidateOldCodes(User user) {
        List<PasswordResetToken> unusedTokens = resetTokenRepository
                .findAllByUser_UserIdAndUsedFalse(user.getUserId());
        for (PasswordResetToken old : unusedTokens) {
            old.setUsed(true);
            old.setUsedAt(OffsetDateTime.now());
        }
        resetTokenRepository.saveAll(unusedTokens);
    }

    /**
     * Validate password strength per Figma rules:
     * - At least 8 characters
     * - At least 1 number
     * - At least 1 symbol (non-alphanumeric)
     */
    private void validatePasswordStrength(String password) {
        if (password.length() < MIN_LENGTH) {
            throw new AppException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        if (!HAS_DIGIT.matcher(password).matches()) {
            throw new AppException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        if (!HAS_SYMBOL.matcher(password).matches()) {
            throw new AppException(ErrorCode.PASSWORD_TOO_WEAK);
        }
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
