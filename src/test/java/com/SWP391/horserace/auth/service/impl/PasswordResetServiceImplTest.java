package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.entity.PasswordResetToken;
import com.SWP391.horserace.auth.repository.PasswordResetTokenRepository;
import com.SWP391.horserace.auth.repository.RefreshTokenRepository;
import com.SWP391.horserace.auth.service.EmailService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository resetTokenRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    @InjectMocks PasswordResetServiceImpl service;

    @Test
    void forgotPassword_withinCooldown_returnsSilently_noEmailNoNewToken() {
        User user = User.builder().email("a@b.com").build();
        when(userRepository.findByEmailAndDeletedFalse("a@b.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.countByUser_UserIdAndCreatedAtAfter(any(), any())).thenReturn(1L);

        // FR-10: cooldown must NOT throw (enumeration-safe) and must NOT send a new code.
        assertThatCode(() -> service.forgotPassword("a@b.com")).doesNotThrowAnyException();
        verify(emailService, never()).sendResetCode(anyString(), anyString());
        verify(resetTokenRepository, never()).save(any());
    }

    @Test
    void resetPassword_missingUppercase_rejectedAsTooWeak() {
        // FR-12: reset must enforce the same strength as registration (needs an uppercase letter).
        assertThatThrownBy(() -> service.resetPassword("a@b.com", "123456", "password1!", "password1!"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_WEAK);
    }

    @Test
    void verifyCode_wrongCode_incrementsAttemptCountAndThrowsInvalid() {
        User user = User.builder().email("a@b.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .codeHash("some-other-hash")
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .attemptCount(0)
                .build();
        when(userRepository.findByEmailAndDeletedFalse("a@b.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.of(token));

        // FR-11: a wrong code bumps attemptCount (persisted) and reports RESET_CODE_INVALID.
        assertThatThrownBy(() -> service.verifyCode("a@b.com", "111111"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESET_CODE_INVALID);
        verify(resetTokenRepository).save(token);
        assertThat(token.getAttemptCount()).isEqualTo(1);
    }
}
