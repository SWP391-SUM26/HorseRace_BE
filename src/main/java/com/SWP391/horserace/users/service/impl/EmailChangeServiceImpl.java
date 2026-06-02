package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.entity.EmailChangeRequest;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.EmailChangeRequestRepository;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.users.service.EmailChangeService;
import com.SWP391.horserace.users.service.UserService;
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
import java.util.UUID;

/**
 * Verified email change. Step 1 ({@link #requestEmailChange}) validates the new address and
 * issues a one-time 6-digit code, persisting only its SHA-256 hash. Step 2
 * ({@link #confirmEmailChange}) verifies the code and applies the new email to {@code app_user}.
 *
 * <p>The code is delivered to the target inbox. With no SMTP configured in this project it is
 * written to the application log (DEV). Swap {@link #deliverCode} for a real mail sender in prod.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChangeServiceImpl implements EmailChangeService {

    /** A pending email-change code is valid for 15 minutes. */
    private static final long CODE_TTL_MINUTES = 15;

    private final EmailChangeRequestRepository emailChangeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestEmailChange(UUID userId, String newEmail) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String normalized = newEmail.trim();
        if (normalized.equalsIgnoreCase(user.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_SAME_AS_CURRENT);
        }
        if (userRepository.existsByEmail(normalized)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        // Supersede any previous outstanding request for this user.
        emailChangeRepository.deleteByUser_UserIdAndConsumedFalse(userId);

        String code = generateCode();
        EmailChangeRequest request = EmailChangeRequest.builder()
                .user(user)
                .newEmail(normalized)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                .build();
        emailChangeRepository.save(request);

        deliverCode(normalized, code);
    }

    @Override
    @Transactional
    public UserResponse confirmEmailChange(UUID userId, String code) {
        EmailChangeRequest request = emailChangeRepository.findByCodeHashAndConsumedFalse(sha256(code))
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        // The code must belong to the caller and still be valid.
        if (!request.getUser().getUserId().equals(userId) || !request.isUsable()) {
            throw new AppException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        // Re-check uniqueness at apply time — the address may have been taken since step 1.
        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        User user = request.getUser();
        user.setEmail(request.getNewEmail());
        userRepository.save(user);

        request.setConsumed(true);
        request.setConsumedAt(OffsetDateTime.now());
        emailChangeRepository.save(request);

        // Same transaction → the saved email is visible to this read.
        return userService.getMyProfile(userId);
    }

    // ---- helpers ----

    /** A 6-digit numeric one-time code (zero-padded). */
    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    /** DEV delivery: log the code. Replace with a real email sender in production. */
    private void deliverCode(String toEmail, String code) {
        log.info("[EMAIL-CHANGE] verification code for {} is {} (valid {} min)",
                toEmail, code, CODE_TTL_MINUTES);
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
