package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.RegisterRequest;
import com.SWP391.horserace.auth.service.AuthService;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier.GooglePrincipal;
import com.SWP391.horserace.auth.service.JwtService;
import com.SWP391.horserace.auth.service.RefreshTokenService;
import com.SWP391.horserace.notifications.entity.Notification;
import com.SWP391.horserace.notifications.repository.NotificationRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.KycStatus;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Role granted to brand-new users created on first Google sign-in. */
    private static final String DEFAULT_GOOGLE_ROLE = "SPECTATOR";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public AuthResponse login(String email, String rawPassword, String userAgent) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        ensureActive(user);
        return issueTokens(user, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(String idToken, String userAgent) {
        GooglePrincipal principal = googleTokenVerifier.verify(idToken);
        User user = userRepository.findByEmailAndDeletedFalse(principal.email())
                .orElseGet(() -> provisionGoogleUser(principal));
        ensureActive(user);
        return issueTokens(user, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, userAgent);
        User user = rotation.user();
        ensureActive(user);
        String accessToken = jwtService.generateAccessToken(user);
        return toResponse(user, accessToken, rotation.rawToken());
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // ---- helpers ----

    private AuthResponse issueTokens(User user, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user, userAgent);
        return toResponse(user, accessToken, refreshToken);
    }

    private AuthResponse toResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getAccessTokenTtlMs() / 1000)
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getRoleCode() : null)
                .build();
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private User provisionGoogleUser(GooglePrincipal principal) {
        Role role = roleRepository.findByRoleCode(DEFAULT_GOOGLE_ROLE)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        User user = User.builder()
                .role(role)
                .userCode("USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .fullName(principal.name())
                .email(principal.email())
                // Random un-guessable hash: Google users authenticate via Google, not this password.
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        Role role = roleRepository.findByRoleCode(DEFAULT_GOOGLE_ROLE)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        User user = User.builder()
                .role(role)
                .userCode("USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.INACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateVerificationToken(user.getEmail());

        String verificationUrl = "http://localhost:8080/api/v1/auth/verify-email?token=" + token;
        Notification notification = Notification.builder()
                .recipient(user)
                .title("Verify your email address")
                .message("Hello " + user.getFullName() + ",\n\nPlease verify your email by clicking the link or making a POST request with the token:\n" 
                        + verificationUrl + "\n\nToken: " + token)
                .channel("EMAIL")
                .deliveryStatus("SENT")
                .sentAt(java.time.OffsetDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        String email;
        try {
            email = jwtService.verifyEmailVerificationToken(token);
        } catch (Exception e) {
            throw new AppException(ErrorCode.EMAIL_VERIFICATION_FAILED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.isDeleted()) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.isDeleted()) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_ACTIVE);
        }

        if (user.getStatus() != UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String token = jwtService.generateVerificationToken(user.getEmail());

        String verificationUrl = "http://localhost:8080/api/v1/auth/verify-email?token=" + token;
        Notification notification = Notification.builder()
                .recipient(user)
                .title("Verify your email address (Resend)")
                .message("Hello " + user.getFullName() + ",\n\nPlease verify your email by clicking the link or making a POST request with the token:\n" 
                        + verificationUrl + "\n\nToken: " + token)
                .channel("EMAIL")
                .deliveryStatus("SENT")
                .sentAt(java.time.OffsetDateTime.now())
                .build();

        notificationRepository.save(notification);
    }
}
