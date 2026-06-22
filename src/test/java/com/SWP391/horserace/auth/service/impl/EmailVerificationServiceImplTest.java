package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.entity.EmailVerificationToken;
import com.SWP391.horserace.auth.repository.EmailVerificationTokenRepository;
import com.SWP391.horserace.auth.service.EmailService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
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
class EmailVerificationServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock EmailVerificationTokenRepository verificationTokenRepository;
    @Mock JavaMailSender mailSender;

    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        // EmailService is a concrete class (not reliably mockable on this JVM); construct a real
        // instance over a mocked JavaMailSender so the best-effort send path is exercised.
        EmailService emailService = new EmailService(mailSender);
        service = new EmailVerificationServiceImpl(userRepository, verificationTokenRepository, emailService);
        ReflectionTestUtils.setField(service, "codeTtlMinutes", 15);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User newUser() {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("rider@example.com")
                .emailVerified(false)
                .build();
    }

    @Test
    void verifyEmail_validCode_setsEmailVerifiedAndMarksTokenUsed() {
        User user = newUser();
        String code = "123456";
        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenId(UUID.randomUUID())
                .user(user)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .used(false)
                .build();

        when(userRepository.findByEmailAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));
        when(verificationTokenRepository.findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId()))
                .thenReturn(Optional.of(token));

        service.verifyEmail(user.getEmail(), code);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    @Test
    void verifyEmail_invalidCode_throwsAndDoesNotVerify() {
        User user = newUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenId(UUID.randomUUID())
                .user(user)
                .codeHash(sha256("999999"))
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .used(false)
                .build();

        when(userRepository.findByEmailAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));
        when(verificationTokenRepository.findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail(user.getEmail(), "123456"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(token.isUsed()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_expiredCode_throws() {
        User user = newUser();
        String code = "123456";
        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenId(UUID.randomUUID())
                .user(user)
                .codeHash(sha256(code))
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .used(false)
                .build();

        when(userRepository.findByEmailAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));
        when(verificationTokenRepository.findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(user.getUserId()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail(user.getEmail(), code))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);

        assertThat(user.isEmailVerified()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void requestVerification_unknownEmail_doesNotThrowAndStoresNothing() {
        when(userRepository.findByEmailAndDeletedFalse("nobody@example.com")).thenReturn(Optional.empty());

        service.requestVerification("nobody@example.com");

        verify(verificationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void requestVerification_knownEmail_storesHashedTokenAndSendsBestEffort() {
        // MimeMessage cannot be inline-mocked on Java 25, so we do NOT stub createMimeMessage().
        // The real EmailService send will fail, but the impl's best-effort try/catch swallows it,
        // so the token MUST still be persisted and the request MUST NOT throw.
        User user = newUser();
        when(userRepository.findByEmailAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));

        service.requestVerification(user.getEmail());

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(verificationTokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();
        assertThat(saved.getCodeHash()).isNotBlank();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now());
    }
}
