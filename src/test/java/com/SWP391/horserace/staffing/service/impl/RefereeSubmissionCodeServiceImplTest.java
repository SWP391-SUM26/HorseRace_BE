package com.SWP391.horserace.staffing.service.impl;

import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.auth.service.EmailService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.entity.RefereeSubmissionCode;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.staffing.repository.RefereeSubmissionCodeRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefereeSubmissionCodeServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock RefereeAssignmentRepository refereeAssignmentRepository;
    @Mock RefereeSubmissionCodeRepository submissionCodeRepository;
    @Mock JavaMailSender mailSender;

    private RefereeSubmissionCodeServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // EmailService is a concrete class; construct a real instance over a mocked JavaMailSender
        // so the best-effort send path is exercised (its try/catch swallows the send failure).
        EmailService emailService = new EmailService(mailSender);
        service = new RefereeSubmissionCodeServiceImpl(
                userRepository, refereeAssignmentRepository, submissionCodeRepository, emailService);
        ReflectionTestUtils.setField(service, "codeTtlMinutes", 10);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "resendThrottleSeconds", 60);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User verifiedReferee() {
        return User.builder().userId(userId).email("ref@example.com").emailVerified(true).build();
    }

    private void confirmedAssignment() {
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, userId, RefereeAssignmentStatus.OFFICIATING)).thenReturn(true);
    }

    // ── requestCode ──

    @Test
    void requestCode_confirmedRefereeVerifiedEmail_hashesAndEmails() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(verifiedReferee()));
        confirmedAssignment();
        when(submissionCodeRepository.findFirstByRace_RaceIdAndReferee_UserIdOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.empty());

        service.requestCode(userId, raceId);

        ArgumentCaptor<RefereeSubmissionCode> captor = ArgumentCaptor.forClass(RefereeSubmissionCode.class);
        verify(submissionCodeRepository).save(captor.capture());
        RefereeSubmissionCode saved = captor.getValue();
        assertThat(saved.getCodeHash()).isNotBlank();
        // hash only — never the plaintext (6-digit codes are 100000–999999)
        assertThat(saved.getCodeHash()).doesNotMatch("\\d{6}");
        assertThat(saved.getRace().getRaceId()).isEqualTo(raceId);
        assertThat(saved.getReferee().getUserId()).isEqualTo(userId);
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void requestCode_unverifiedEmail_throws() {
        User unverified = User.builder().userId(userId).email("ref@example.com").emailVerified(false).build();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(unverified));
        confirmedAssignment();

        assertThatThrownBy(() -> service.requestCode(userId, raceId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_EMAIL_UNVERIFIED);

        verify(submissionCodeRepository, never()).save(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void requestCode_notConfirmedReferee_throws() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(verifiedReferee()));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, userId, RefereeAssignmentStatus.OFFICIATING)).thenReturn(false);

        assertThatThrownBy(() -> service.requestCode(userId, raceId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_NOT_ASSIGNED);

        verify(submissionCodeRepository, never()).save(any());
    }

    @Test
    void requestCode_withinThrottle_throws() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(verifiedReferee()));
        confirmedAssignment();
        RefereeSubmissionCode recent = RefereeSubmissionCode.builder()
                .codeId(UUID.randomUUID())
                .createdAt(OffsetDateTime.now().minusSeconds(5)) // < 60s ago
                .build();
        when(submissionCodeRepository.findFirstByRace_RaceIdAndReferee_UserIdOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> service.requestCode(userId, raceId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_CODE_THROTTLED);

        verify(submissionCodeRepository, never()).save(any());
    }

    // ── validateAndConsume ──

    private RefereeSubmissionCode unconsumedCode(String plaintext, int attempts, OffsetDateTime expiresAt) {
        return RefereeSubmissionCode.builder()
                .codeId(UUID.randomUUID())
                .race(Race.builder().raceId(raceId).build())
                .referee(verifiedReferee())
                .codeHash(sha256(plaintext))
                .attemptCount(attempts)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void validateAndConsume_correctCode_marksConsumed() {
        RefereeSubmissionCode code = unconsumedCode("123456", 0, OffsetDateTime.now().plusMinutes(10));
        when(submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.of(code));
        when(submissionCodeRepository.markConsumed(any(), any())).thenReturn(1);

        service.validateAndConsume(userId, raceId, "123456");

        verify(submissionCodeRepository).markConsumed(any(), any());
    }

    @Test
    void validateAndConsume_wrongCode_incrementsAttempt_throwsInvalid() {
        RefereeSubmissionCode code = unconsumedCode("123456", 1, OffsetDateTime.now().plusMinutes(10));
        when(submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validateAndConsume(userId, raceId, "000000"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_CODE_INVALID);

        assertThat(code.getAttemptCount()).isEqualTo(2);
        verify(submissionCodeRepository).save(code);
        verify(submissionCodeRepository, never()).markConsumed(any(), any());
    }

    @Test
    void validateAndConsume_expired_throwsInvalid() {
        RefereeSubmissionCode code = unconsumedCode("123456", 0, OffsetDateTime.now().minusMinutes(1));
        when(submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validateAndConsume(userId, raceId, "123456"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_CODE_INVALID);

        verify(submissionCodeRepository, never()).markConsumed(any(), any());
    }

    @Test
    void validateAndConsume_noneOrAlreadyConsumed_throwsInvalid() {
        when(submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndConsume(userId, raceId, "123456"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_CODE_INVALID);
    }

    @Test
    void validateAndConsume_consumeRaceLost_throwsInvalid() {
        // hash matches but the atomic CAS stamps 0 rows — a concurrent submit already used it.
        RefereeSubmissionCode code = unconsumedCode("123456", 0, OffsetDateTime.now().plusMinutes(10));
        when(submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.of(code));
        when(submissionCodeRepository.markConsumed(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.validateAndConsume(userId, raceId, "123456"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_CODE_INVALID);
    }

    @Test
    void validateAndConsume_tooManyAttempts_throwsLocked() {
        RefereeSubmissionCode code = unconsumedCode("123456", 5, OffsetDateTime.now().plusMinutes(10));
        when(submissionCodeRepository
                .findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(raceId, userId))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validateAndConsume(userId, raceId, "123456"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFEREE_CODE_LOCKED);

        verify(submissionCodeRepository, never()).markConsumed(any(), any());
    }
}
