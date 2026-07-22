package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterResponse;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier;
import com.SWP391.horserace.auth.service.JwtService;
import com.SWP391.horserace.auth.service.RefreshTokenService;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import com.SWP391.horserace.onboarding.repository.MembershipApplicationRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock com.SWP391.horserace.users.service.UserCodeGenerator userCodeGenerator;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock GoogleTokenVerifier googleTokenVerifier;
    @Mock JockeyProfileRepository jockeyProfileRepository;
    @Mock MembershipApplicationRepository membershipApplicationRepository;
    @Mock com.SWP391.horserace.owner.repository.OwnerProfileRepository ownerProfileRepository;
    @Mock AttachmentService attachmentService;

    @InjectMocks AuthServiceImpl service;

    private RegisterJockeyRequest jockeyRequest() {
        return new RegisterJockeyRequest(
                "Jockey@Example.com", "Passw0rd!", "Passw0rd!", "Loop", "Rider",
                22, 54.43, "VN", 3, "Flat", null, null, true);
    }

    @Test
    void registerJockey_createsPendingUserProfileAndApplication_withoutIssuingTokens() {
        Role jockeyRole = Role.builder().roleCode("JOCKEY").build();
        when(userRepository.existsByEmail("jockey@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(jockeyRole));
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("{bcrypt}hash");

        RegisterResponse resp = service.registerJockey(jockeyRequest(), null, null);

        // Response carries no tokens (RegisterResponse has none) and reflects the jockey.
        assertThat(resp.email()).isEqualTo("jockey@example.com");
        assertThat(resp.role()).isEqualTo("JOCKEY");
        assertThat(resp.fullName()).isEqualTo("Loop Rider");

        // User saved as PENDING (cannot log in until approved).
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(userCap.getValue().getEmail()).isEqualTo("jockey@example.com");

        // Jockey profile persisted with the self-registration fields (weight is kg, stored as-is).
        ArgumentCaptor<JockeyProfile> profCap = ArgumentCaptor.forClass(JockeyProfile.class);
        verify(jockeyProfileRepository).save(profCap.capture());
        assertThat(profCap.getValue().getAge()).isEqualTo(22);
        assertThat(profCap.getValue().getNationality()).isEqualTo("VN");
        assertThat(profCap.getValue().getApplicationRidingStyle()).isEqualTo("Flat");
        assertThat(profCap.getValue().getExperienceYrs()).isEqualTo(3);
        assertThat(profCap.getValue().getBodyWeight().doubleValue()).isEqualTo(54.43); // kg, stored as-is

        // Onboarding application filed for the referee queue.
        ArgumentCaptor<MembershipApplication> appCap = ArgumentCaptor.forClass(MembershipApplication.class);
        verify(membershipApplicationRepository).save(appCap.capture());
        assertThat(appCap.getValue().getRequestedRole()).isEqualTo(RequestedRole.JOCKEY);
        assertThat(appCap.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(appCap.getValue().getApplicationCode()).startsWith("APP-");
        assertThat(appCap.getValue().getEmail()).isEqualTo("jockey@example.com");

        // Crucially: NO tokens issued.
        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void registerSpectator_duplicatePhone_throws() {
        RegisterSpectatorRequest request = new RegisterSpectatorRequest(
                "Sam Spectator", "Sam@Example.com", "0912345678", "Passw0rd!", "Passw0rd!", true);
        when(userRepository.existsByEmail("sam@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> service.registerSpectator(request, "ua"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSpectator_normalizesPhone_soFormattingCannotBypassUnique() {
        // FR-11: dashed/spaced phone stripped to digits before existsByPhone, so a formatted
        // variant of an already-registered number is still rejected as a duplicate.
        RegisterSpectatorRequest request = new RegisterSpectatorRequest(
                "Sam Spectator", "Sam@Example.com", "09 12-345-678", "Passw0rd!", "Passw0rd!", true);
        when(userRepository.existsByEmail("sam@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> service.registerSpectator(request, "ua"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_pendingJockey_isBlockedWithPendingApprovalError() {
        User pending = User.builder()
                .email("jockey@example.com").passwordHash("{bcrypt}hash")
                .status(UserStatus.PENDING).build();
        when(userRepository.findByEmailAndDeletedFalse("jockey@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Passw0rd!", "{bcrypt}hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login("jockey@example.com", "Passw0rd!", "ua"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_PENDING_APPROVAL);

        verify(jwtService, never()).generateAccessToken(any());
    }
}
