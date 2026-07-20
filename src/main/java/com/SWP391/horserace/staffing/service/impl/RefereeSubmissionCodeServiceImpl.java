package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.auth.service.EmailService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.entity.RefereeSubmissionCode;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.staffing.repository.RefereeSubmissionCodeRepository;
import com.SWP391.horserace.staffing.service.RefereeSubmissionCodeService;
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
import java.util.UUID;

/**
 * Implements the emailed OTP submission-code lifecycle (CN3). Mirrors
 * {@code EmailVerificationServiceImpl}: {@link SecureRandom} 6-digit code, SHA-256 Base64 hash,
 * TTL / attempt-limit / resend-throttle from {@code application.properties}. The plaintext code
 * is only ever emailed — never persisted or returned.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefereeSubmissionCodeServiceImpl implements RefereeSubmissionCodeService {

    private final UserRepository userRepository;
    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final RefereeSubmissionCodeRepository submissionCodeRepository;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.referee-code.code-ttl-minutes:10}")
    private int codeTtlMinutes;

    @Value("${app.referee-code.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.referee-code.resend-throttle-seconds:60}")
    private int resendThrottleSeconds;

    @Override
    @Transactional
    public void requestCode(UUID refereeUserId, UUID raceId) {
        if (refereeUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findByUserIdAndDeletedFalse(refereeUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Only a CONFIRMED referee on this race may request a code.
        if (!refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatus(
                raceId, refereeUserId, RefereeAssignmentStatus.CONFIRMED)) {
            throw new AppException(ErrorCode.REFEREE_NOT_ASSIGNED);
        }

        // The code goes to their verified email — refuse if unverified.
        if (!user.isEmailVerified()) {
            throw new AppException(ErrorCode.REFEREE_EMAIL_UNVERIFIED);
        }

        // Resend throttle: reject if the previous code was issued too recently.
        Optional<RefereeSubmissionCode> last = submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdOrderByCreatedAtDesc(raceId, refereeUserId);
        if (last.isPresent() && last.get().getCreatedAt() != null
                && last.get().getCreatedAt().isAfter(OffsetDateTime.now().minusSeconds(resendThrottleSeconds))) {
            throw new AppException(ErrorCode.REFEREE_CODE_THROTTLED);
        }

        String code = generateSixDigitCode();
        RefereeSubmissionCode entity = RefereeSubmissionCode.builder()
                .race(Race.builder().raceId(raceId).build()) // FK reference — only the id is needed
                .referee(user)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(codeTtlMinutes))
                .build();
        submissionCodeRepository.save(entity);

        try {
            emailService.sendRefereeCode(user.getEmail(), code);
        } catch (Exception e) {
            log.error("Failed to send referee submission code to {} (continuing)", user.getEmail(), e);
        }

        log.info("Referee submission code issued for user {} on race {}", refereeUserId, raceId);
    }

    @Override
    @Transactional
    public void validateAndConsume(UUID refereeUserId, UUID raceId, String providedCode) {
        RefereeSubmissionCode code = submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, refereeUserId)
                .orElseThrow(() -> new AppException(ErrorCode.REFEREE_CODE_INVALID));

        if (code.isExpired()) {
            throw new AppException(ErrorCode.REFEREE_CODE_INVALID);
        }

        if (code.getAttemptCount() >= maxAttempts) {
            throw new AppException(ErrorCode.REFEREE_CODE_LOCKED);
        }

        if (providedCode == null || !sha256(providedCode).equals(code.getCodeHash())) {
            code.setAttemptCount(code.getAttemptCount() + 1);
            submissionCodeRepository.save(code);
            throw new AppException(ErrorCode.REFEREE_CODE_INVALID);
        }

        // Atomic single-use consume (compare-and-swap): 0 rows means a concurrent submit already used it.
        int consumed = submissionCodeRepository.markConsumed(code.getCodeId(), OffsetDateTime.now());
        if (consumed == 0) {
            throw new AppException(ErrorCode.REFEREE_CODE_INVALID);
        }
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
